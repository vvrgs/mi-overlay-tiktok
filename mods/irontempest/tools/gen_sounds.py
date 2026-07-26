#!/usr/bin/env python3
"""
gen_sounds.py — Procedural sound designer for the irontempest Forge mod.

Generates 26 OGG/Vorbis files (44100 Hz, MONO, float32) with layered synthesis:
brown/pink noise beds, detuned oscillators, exponential pitch envelopes, tanh
saturation, multitap delays and hand-built ADSR envelopes.

Pure numpy DSP (no scipy): filters are applied in the FFT domain with
Butterworth magnitude responses. Noise is always filtered BEFORE enveloping so
transients stay razor sharp (no zero-phase pre-ring on attacks). FFT filtering
is circular, which is exactly what seamless loops want.

Loops use a circular crossfade (render N+F, equal-power blend of the last F
over the first F, trim to N), then the loop is rotated to the adjacent-sample
pair with minimal difference and the wrap point is pinned so that
|last - first| < 0.01 survives Vorbis encoding.

Validation re-reads every OGG and checks duration, channels, peak and loop
wrap; files are re-rendered/re-scaled automatically until everything passes.
"""

import os
import sys
import numpy as np
import soundfile as sf

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(
    HERE, "..", "src", "main", "resources", "assets", "irontempest", "sounds"))

# ----------------------------------------------------------------------------
# Core helpers
# ----------------------------------------------------------------------------

def nsamp(dur):
    return int(round(dur * SR))

def taxis(n):
    return np.arange(n) / SR

# --- FFT-domain Butterworth-magnitude filters (pure numpy, zero phase) ------

def _shape(x, mag):
    X = np.fft.rfft(x)
    return np.fft.irfft(X * mag, n=len(x))

def _f(n):
    return np.fft.rfftfreq(n, 1.0 / SR)

def _mag_lp(f, fc, order):
    return 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))

def _mag_hp(f, fc, order):
    fs = np.maximum(f, 1e-9)
    return 1.0 / np.sqrt(1.0 + (fc / fs) ** (2 * order))

def lowpass(x, fc, order=4):
    return _shape(x, _mag_lp(_f(len(x)), fc, order))

def highpass(x, fc, order=4):
    return _shape(x, _mag_hp(_f(len(x)), fc, order))

def bandpass(x, f1, f2, order=3):
    f = _f(len(x))
    return _shape(x, _mag_lp(f, f2, order) * _mag_hp(f, f1, order))

# --- Noise colours ----------------------------------------------------------

def white(rng, n):
    return rng.standard_normal(n)

def _spectral_noise(rng, n, slope, hp=18.0):
    """slope: exponent on 1/f (0.5 => pink, 1.0 => brown)."""
    w = rng.standard_normal(n)
    f = _f(n)
    mag = np.zeros_like(f)
    mag[1:] = (f[1] / f[1:]) ** slope
    mag *= _mag_hp(f, hp, 2)          # kill DC / subsonic drift
    y = _shape(w, mag)
    s = np.std(y)
    return y / (s + 1e-12)

def pink(rng, n):
    return _spectral_noise(rng, n, 0.5)

def brown(rng, n):
    return _spectral_noise(rng, n, 1.0)

def slow_noise(rng, n, fc):
    """Smoothed random control signal, unit std."""
    y = lowpass(rng.standard_normal(n), fc, 2)
    return y / (np.std(y) + 1e-12)

# --- Oscillators / envelopes ------------------------------------------------

def phase_of(freq):
    """Integrate an instantaneous-frequency array into phase."""
    return 2.0 * np.pi * np.cumsum(freq) / SR

def sweep_exp(n, f0, f1, T=None):
    t = taxis(n)
    T = T if T is not None else (t[-1] if n > 1 else 1.0)
    return f0 * (f1 / f0) ** (np.minimum(t, T) / T)

def env_exp(n, tau, t0=0.0):
    t = taxis(n)
    e = np.exp(-np.maximum(t - t0, 0.0) / tau)
    e[t < t0] = 0.0
    return e

def env_attack(n, atk):
    t = taxis(n)
    a = np.minimum(t / max(atk, 1e-6), 1.0)
    return 0.5 - 0.5 * np.cos(np.pi * a)

def adsr(n, a, d, s_level, r):
    """Classic ADSR over n samples (release occupies the last r seconds)."""
    t = taxis(n)
    T = n / SR
    e = np.ones(n) * s_level
    ai = t < a
    e[ai] = t[ai] / max(a, 1e-9)
    di = (t >= a) & (t < a + d)
    e[di] = 1.0 + (s_level - 1.0) * (t[di] - a) / max(d, 1e-9)
    ri = t > (T - r)
    e[ri] *= np.clip((T - t[ri]) / max(r, 1e-9), 0.0, 1.0)
    return e

def fade_io(x, fin=0.004, fout=0.04):
    """Raised-cosine fades (>= 3 ms) for one-shots."""
    y = x.copy()
    ni = max(nsamp(fin), nsamp(0.003))
    no = max(nsamp(fout), nsamp(0.003))
    wi = 0.5 - 0.5 * np.cos(np.pi * np.arange(ni) / ni)
    wo = 0.5 - 0.5 * np.cos(np.pi * np.arange(no) / no)
    y[:ni] *= wi
    y[-no:] *= wo[::-1]
    return y

def sat(x, drive=1.6):
    return np.tanh(drive * x) / np.tanh(drive)

def taps(x, tap_list):
    """Multitap delay (feed-forward)."""
    y = x.copy()
    for dt, g in tap_list:
        d = nsamp(dt)
        if d < len(x):
            y[d:] += g * x[:-d]
    return y

def comb_chain(x, tap_list, floor=0.02):
    """Sequential feedback combs => cheap long 'reverb'."""
    y = x.copy()
    for dt, g in tap_list:
        d = nsamp(dt)
        src = y.copy()
        k = 1
        while g ** k > floor and k * d < len(x):
            y[k * d:] += (g ** k) * src[:len(x) - k * d]
            k += 1
    return y

def grains(rng, n, count, t0, t1, lmin, lmax, amp_fn, bias=1.0):
    """Random short noise bursts (crackle / debris)."""
    out = np.zeros(n)
    for _ in range(count):
        pos = t0 + (t1 - t0) * (rng.random() ** bias)
        L = nsamp(rng.uniform(lmin, lmax))
        i = nsamp(pos)
        if i >= n - 8:
            continue
        L = min(L, n - i)
        g = rng.standard_normal(L) * np.exp(-np.arange(L) / (max(L, 8) / 4.0))
        e = min(nsamp(0.001), L // 2)
        if e > 0:
            g[:e] *= np.linspace(0, 1, e)
        out[i:i + L] += amp_fn(pos) * rng.uniform(0.5, 1.0) * g
    return out

def norm(x, peak=1.0):
    return x * (peak / (np.max(np.abs(x)) + 1e-12))

# --- Seamless loop construction ---------------------------------------------

def make_loop(render, dur, xfade=0.35):
    """render(m) -> m samples of continuous signal. Circular crossfade + rotate."""
    n = nsamp(dur)
    fN = nsamp(xfade)
    y = render(n + fN)
    th = np.linspace(0.0, np.pi / 2.0, fN)
    z = y[:n].copy()
    z[:fN] = np.sin(th) * z[:fN] + np.cos(th) * y[n:n + fN]   # equal power
    # rotate the (circularly continuous) loop so the file boundary lands on
    # an adjacent-sample pair with a tiny amplitude step INSIDE a quiet
    # neighbourhood (Vorbis quantization noise scales with local energy, so a
    # quiet wrap point keeps |last - first| far below 0.01 after encoding)
    dif = np.abs(z - np.roll(z, 1))
    w = 600
    loc = np.sqrt(np.convolve(z * z, np.ones(w) / w, mode="same") + 1e-12)
    score = dif + 0.15 * loc + 0.05 * (np.abs(z) + np.abs(np.roll(z, 1)))
    i = int(np.argmin(score))
    z = np.roll(z, -i)
    # pin the wrap exactly (linear correction over ~1 ms, inaudible)
    K = 48
    z[-K:] -= np.linspace(0.0, 1.0, K) * (z[-1] - z[0])
    return z

# ----------------------------------------------------------------------------
# Sound designs
# ----------------------------------------------------------------------------

def _explosion_design(seed, T, sub_f0, sub_f1, sub2_f0, sub2_f1,
                      crk_count, crk_t1, crk_bias, crk_lo, crk_hi, rum_tau):
    """Close-range explosion: transient + sub-boom + crackle + rumble.

    Parametrized so REAL variations can be rendered (different noise seed,
    different 32-60 Hz sub-boom pitch, different crackle pattern) — the RNG
    call order matches the original v1 render exactly, so seed 101 with the
    v1 parameters reproduces explosion_near.ogg bit-for-bit."""
    n = nsamp(T)
    t = taxis(n)
    rng = np.random.default_rng(seed)
    # L1: brutal 5 ms transient + crunch body
    trans = white(rng, n) * (1.6 * np.exp(-t / 0.005) + 0.55 * np.exp(-t / 0.035))
    trans = lowpass(trans, 9000, 2)
    # L2: sub-boom pitch drop, ~1.8 s decay
    fsub = sweep_exp(n, sub_f0, sub_f1, 1.8)
    sub = np.sin(phase_of(fsub)) * env_exp(n, 0.42) * env_attack(n, 0.004)
    sub += 0.35 * np.sin(phase_of(sweep_exp(n, sub2_f0, sub2_f1, 0.5))) \
        * env_exp(n, 0.12)
    # L3: crackle bursts
    crk = grains(rng, n, crk_count, 0.0, crk_t1, 0.002, 0.011,
                 lambda p: max(0.05, 1.0 - p / (crk_t1 + 0.05)), bias=crk_bias)
    crk = bandpass(crk, crk_lo, crk_hi, 2)
    # L4: rumble tail
    rum = lowpass(brown(rng, n), 170, 3) * env_exp(n, rum_tau) * env_attack(n, 0.02)
    mix = 1.35 * trans + 1.15 * sub + 0.55 * crk + 0.95 * norm(rum)
    return fade_io(sat(mix, 1.8), 0.003, 0.14)

def s_explosion_near():
    return _explosion_design(101, 2.6, 55.0, 35.0, 96.0, 52.0,
                             60, 0.7, 1.8, 700, 5200, 0.85)

def s_explosion_near_1():
    # tighter, angrier variant: sub 60 -> 38 Hz, denser brighter early crackle
    return _explosion_design(201, 2.45, 60.0, 38.0, 112.0, 58.0,
                             88, 0.55, 1.3, 900, 6200, 0.72)

def s_explosion_near_2():
    # heavier variant: sub sinks to 32 Hz, sparse darker late crackle,
    # longer rumble tail
    return _explosion_design(202, 2.75, 47.0, 32.0, 84.0, 45.0,
                             46, 0.95, 2.2, 600, 4300, 0.98)

def s_explosion_far():
    T, n = 3.2, nsamp(3.2)
    t = taxis(n)
    rng = np.random.default_rng(102)
    boom = np.sin(phase_of(sweep_exp(n, 52.0, 33.0, 1.4))) * env_exp(n, 0.55)
    boom += 0.5 * np.sin(phase_of(sweep_exp(n, 78.0, 44.0, 0.8))) * env_exp(n, 0.3)
    body = norm(lowpass(brown(rng, n), 320, 3)) * env_exp(n, 0.7)
    mix = (1.0 * boom + 0.9 * body) * env_attack(n, 0.015)
    mix = lowpass(mix, 480, 4)                       # everything < 500 Hz
    mix = taps(mix, [(0.180, 0.45), (0.310, 0.28)])  # soft double echo
    mix *= env_exp(n, 1.15)                          # let the tail die inside file
    return fade_io(sat(mix, 1.25), 0.008, 0.30)

def _whistle_design(seed, T, f0, f1, vrate, vd0, vd1, h2, cresc_pow, trem):
    """Falling-shell whistle; parametrized pitch curve and vibrato so real
    variations can be rendered (v1 = seed 103 parameters, bit-for-bit)."""
    n = nsamp(T)
    t = taxis(n)
    rng = np.random.default_rng(seed)
    base = sweep_exp(n, f0, f1, T)
    vib_depth = vd0 + vd1 * (t / T)                  # growing vibrato
    f = base * (1.0 + vib_depth * np.sin(2 * np.pi * vrate * t))
    ph = phase_of(f)
    tone = np.sin(ph) + h2 * np.sin(2.0 * ph + 0.7)
    nb = slow_noise(rng, n, 130)                     # narrowband noise rides
    band = nb * np.cos(ph + 1.3)                     # the same freq curve
    cresc = 0.07 + 0.93 * (t / T) ** cresc_pow       # dramatic doppler swell
    mix = (tone + 0.55 * band) * cresc
    mix *= 1.0 + 0.10 * np.sin(2 * np.pi * trem * t + 1.0)
    return fade_io(mix, 0.010, 0.015)

def s_shell_whistle():
    return _whistle_design(103, 2.2, 1400.0, 280.0, 6.0, 0.004, 0.030,
                           0.28, 2.3, 6.0)

def s_shell_whistle_1():
    # variant: 1600 -> 350 Hz curve, faster deeper 8.5 Hz vibrato,
    # earlier swell, slower tremolo
    return _whistle_design(203, 2.0, 1600.0, 350.0, 8.5, 0.010, 0.038,
                           0.34, 2.0, 4.4)

def s_cannon_fire():
    T, n = 1.4, nsamp(1.4)
    t = taxis(n)
    rng = np.random.default_rng(104)
    crack = white(rng, n) * np.exp(-t / 0.0045) * 1.8          # 8 ms crack
    thump = np.sin(phase_of(sweep_exp(n, 118.0, 57.0, 0.22))) * env_exp(n, 0.20)
    metal = np.zeros(n)
    for fq, tau, a in ((321.0, 0.075, 0.34), (473.0, 0.06, 0.28),
                       (812.0, 0.045, 0.22), (1290.0, 0.03, 0.12)):
        metal += a * np.sin(2 * np.pi * fq * t + rng.uniform(0, 6.28)) \
                 * env_exp(n, tau, 0.004)
    tail = norm(lowpass(brown(rng, n), 260, 3)) * env_exp(n, 0.28) \
        * env_attack(n, 0.005)
    mix = 1.25 * crack + 1.2 * thump + metal + 0.85 * tail
    return fade_io(sat(mix, 1.7), 0.003, 0.10)

def s_tank_engine():
    def render(m):
        t = taxis(m)
        rng = np.random.default_rng(105)
        j1 = slow_noise(rng, m, 1.3)                 # slow idle jitter
        j2 = slow_noise(rng, m, 0.8)
        rate = 4.5 * (1.0 + 0.16 * j1)               # irregular 4.5 Hz idle
        am = 0.70 + 0.30 * (0.5 + 0.5 * np.sin(phase_of(rate))) ** 1.6
        eng = np.zeros(m)
        for fq, a in ((28.0, 1.0), (56.0, 0.60), (84.0, 0.44),
                      (112.0, 0.33), (140.0, 0.18), (168.0, 0.11)):
            fi = fq * (1.0 + 0.006 * j2)             # softened-saw partials
            eng += a * np.sin(phase_of(fi) + rng.uniform(0, 6.28))
        eng *= am
        gnd = lowpass(white(rng, m), 95, 3)
        gnd = gnd / (np.std(gnd) + 1e-12) * 0.5 * (0.8 + 0.2 * am)
        return sat(eng + gnd, 1.5)
    return make_loop(render, 3.0, 0.45)

def s_tank_landing():
    T, n = 1.8, nsamp(1.8)
    t = taxis(n)
    rng = np.random.default_rng(106)
    thud = np.sin(phase_of(sweep_exp(n, 74.0, 41.0, 0.3))) * env_exp(n, 0.26) * 1.25
    clang = np.zeros(n)
    for fq, tau, a in ((163.0, 0.30, 0.30), (166.5, 0.28, 0.28),   # beating pair
                       (287.0, 0.22, 0.24), (291.5, 0.20, 0.22),   # beating pair
                       (414.0, 0.16, 0.18), (562.0, 0.12, 0.14),
                       (731.0, 0.09, 0.10)):
        clang += a * np.sin(2 * np.pi * fq * t + rng.uniform(0, 6.28)) \
                 * env_exp(n, tau, 0.003)
    debris = grains(rng, n, 38, 0.05, 1.05, 0.004, 0.020,
                    lambda p: max(0.05, 1.0 - p / 1.15), bias=1.4)
    debris = bandpass(debris, 750, 4500, 2)
    rum = norm(lowpass(brown(rng, n), 130, 3)) * env_exp(n, 0.45) \
        * env_attack(n, 0.006)
    crack = white(rng, n) * np.exp(-t / 0.004) * 0.9
    mix = thud + clang + 0.5 * debris + 0.8 * rum + crack
    return fade_io(sat(mix, 1.6), 0.003, 0.12)

def s_missile_launch():
    T, n = 2.8, nsamp(2.8)
    t = taxis(n)
    rng = np.random.default_rng(107)
    roar_env = env_attack(n, 0.4)                      # 0 -> max in 400 ms
    fadeout = np.clip((T - t) / 0.9, 0.0, 1.0) ** 1.5  # doppler recede
    w = white(rng, n)
    bright = lowpass(w, 5500, 2)
    dark = lowpass(w, 650, 3)
    mixf = np.clip((2.05 - t) / 0.9, 0.0, 1.0) ** 0.8  # progressive lowpass
    roar = norm(bright) * mixf + norm(dark) * (1.0 - 0.35 * mixf)
    reso = norm(bandpass(white(rng, n), 68, 118, 3)) * 1.1
    drone = 0.16 * np.sin(phase_of(90.0 * (1.0 + 0.012 *
                                           np.sin(2 * np.pi * 3.1 * t))))
    pops = grains(rng, n, 95, 0.05, 2.55, 0.0015, 0.006,
                  lambda p: 0.9, bias=1.0)
    pops = lowpass(pops, 3200, 2) * roar_env
    mix = (0.95 * roar + 0.9 * reso + drone) * roar_env * (0.35 + 0.65 * fadeout)
    mix += 0.65 * pops * fadeout
    return fade_io(sat(mix, 1.4), 0.010, 0.10)

def s_missile_loop():
    def render(m):
        t = taxis(m)
        rng = np.random.default_rng(108)
        jet = norm(bandpass(pink(rng, m), 100, 900, 3))
        turbine = 1.0 + 0.24 * np.sin(2 * np.pi * 47.0 * t)
        tone = 0.10 * np.sin(2 * np.pi * 94.0 * t) \
            + 0.07 * np.sin(2 * np.pi * 47.0 * t + 1.1)
        fw = 1200.0 * (1.0 + 0.007 * np.sin(2 * np.pi * 2.3 * t))
        whistle = 0.045 * np.sin(phase_of(fw))         # very subtle 1.2 kHz
        return jet * turbine + tone + whistle
    return make_loop(render, 2.0, 0.40)

def _mlrs_whoosh(seed, T, f0, fpk, f1, t_up, t_dn, nb1, nb2, ratio,
                 dec_t0, dec_tau, hiss_t0, hiss_tau):
    """Rocket whoosh: bandpass sweep f0 -> fpk -> f1 (ring-mod technique).
    Parametrized for real variations (v1 = seed 109 parameters)."""
    n = nsamp(T)
    t = taxis(n)
    rng = np.random.default_rng(seed)
    t_set = t_up + t_dn
    f = np.empty(n)
    m1, m2 = t < t_up, (t >= t_up) & (t < t_set)
    f[m1] = f0 * (fpk / f0) ** (t[m1] / t_up)
    f[m2] = fpk * (f1 / fpk) ** ((t[m2] - t_up) / t_dn)
    f[t >= t_set] = f1
    nb = slow_noise(rng, n, nb1)
    whoosh = nb * np.sin(phase_of(f)) + 0.4 * slow_noise(rng, n, nb2) \
        * np.cos(phase_of(f * ratio))
    wenv = env_attack(n, 0.035) * np.where(
        t < dec_t0, 1.0, np.exp(-(t - dec_t0) / dec_tau))
    hiss = norm(highpass(white(rng, n), 1900, 2)) \
        * env_exp(n, hiss_tau, hiss_t0) * 0.5
    body = norm(bandpass(white(rng, n), 350, 3200, 2)) * wenv * 0.4
    mix = whoosh * wenv + body + hiss
    return fade_io(sat(mix, 1.3), 0.006, 0.05)

def s_mlrs_launch():
    return _mlrs_whoosh(109, 1.1, 300.0, 2500.0, 600.0, 0.28, 0.22,
                        260, 420, 1.12, 0.45, 0.16, 0.32, 0.24)

def s_mlrs_launch_1():
    # variant: snappier rise to a higher 2950 Hz peak, lower settle,
    # breathier faster decay
    return _mlrs_whoosh(209, 1.0, 250.0, 2950.0, 460.0, 0.21, 0.26,
                        320, 520, 1.09, 0.40, 0.14, 0.26, 0.20)

def s_laser_charge():
    T, n = 4.2, nsamp(4.2)
    t = taxis(n)
    rng = np.random.default_rng(110)
    f = sweep_exp(n, 180.0, 1500.0, 4.0)               # 4 s exponential rise
    ph = phase_of(f)
    tone = np.sin(ph) + 0.45 * np.sin(2 * ph + 0.4) + 0.22 * np.sin(3 * ph + 1.9)
    rate = 8.0 * (30.0 / 8.0) ** (t / T)               # accelerating pulses
    gate = (0.5 + 0.5 * np.sin(phase_of(rate))) ** 1.6
    tone *= 0.45 + 0.55 * gate
    shim = norm(highpass(white(rng, n), 6000, 2)) \
        * (0.55 + 0.45 * np.sin(2 * np.pi * 2.2 * t)) * (t / T) ** 2 * 0.5
    rise = 0.30 + 0.70 * (t / T) ** 1.4
    mix = (tone + shim) * rise
    clim = np.zeros(n)                                  # bright final chord
    for fq, a in ((1500.0, 0.9), (1875.0, 0.7), (2250.0, 0.6), (3000.0, 0.45)):
        clim += a * np.sin(2 * np.pi * fq * t + rng.uniform(0, 6.28))
    clim *= env_attack(n, 0.012) * (t > 3.93)
    spark = norm(highpass(white(rng, n), 5000, 2)) * (t > 3.93) * 0.35
    mix += (clim + spark) * 0.9
    return fade_io(mix, 0.008, 0.045)

def s_laser_beam():
    def render(m):
        t = taxis(m)
        rng = np.random.default_rng(111)
        mod = 1.0 + 0.30 * np.sin(2 * np.pi * 0.7 * t)   # slow brightness LFO
        def saw(f0, amp):
            lo, hi = np.zeros(m), np.zeros(m)
            k = 1
            while k * f0 < 0.42 * SR and k <= 30:
                p = 2 * np.pi * k * f0 * t + rng.uniform(0, 6.28)
                (lo if k < 4 else hi)[:] += (amp / k) * np.sin(p)
                k += 1
            return lo, hi
        lo = np.zeros(m); hi = np.zeros(m)
        for f0, a in ((220.0, 0.5), (221.5, 0.5), (440.0, 0.33)):
            l, h = saw(f0, a)
            lo += l; hi += h
        hiss = norm(highpass(white(rng, m), 3000, 2)) * 0.15
        sub = 0.40 * np.sin(2 * np.pi * 80.0 * t)
        return sat(lo + hi * mod + hiss * mod + sub, 1.3)
    return make_loop(render, 2.0, 0.50)

def s_warp_in():
    T, n = 1.6, nsamp(1.6)
    t = taxis(n)
    rng = np.random.default_rng(112)
    t0 = 1.2
    swell = np.exp((t - t0) / 0.22)                     # reverse-reverb swell
    swell[t >= t0] = np.clip(1.0 - (t[t >= t0] - t0) / 0.02, 0.0, 1.0)
    chord = np.zeros(n)
    for fq in (110.0, 165.0, 220.0, 275.0, 330.0):
        fq *= 1.0 + rng.uniform(-0.003, 0.003)
        chord += np.sin(2 * np.pi * fq * t + rng.uniform(0, 6.28))
    wash = norm(lowpass(white(rng, n), 2600, 2))
    pre = (0.32 * chord + 0.85 * wash) * swell
    ping = np.zeros(n)                                  # bright metallic ping
    for fq, tau, a in ((1180.0, 0.30, 1.0), (1772.0, 0.24, 0.72),
                       (2651.0, 0.17, 0.5), (3547.0, 0.12, 0.36),
                       (5310.0, 0.08, 0.22)):
        fq *= 1.0 + rng.uniform(-0.002, 0.002)
        ping += a * np.sin(2 * np.pi * fq * t + rng.uniform(0, 6.28)) \
            * env_exp(n, tau, t0)
    boom = np.sin(phase_of(sweep_exp(n, 62.0, 38.0, 0.35))) * env_exp(n, 0.13, t0)
    sparkle = norm(highpass(white(rng, n), 4200, 2)) * env_exp(n, 0.09, t0)
    mix = pre + 1.1 * ping + 1.15 * boom + 0.4 * sparkle
    return fade_io(sat(mix, 1.3), 0.003, 0.05)

def s_warp_out():
    T, n = 1.6, nsamp(1.6)
    t = taxis(n)
    rng = np.random.default_rng(113)
    boom = np.sin(phase_of(sweep_exp(n, 64.0, 35.0, 0.3))) * env_exp(n, 0.15) * 1.2
    thmp = norm(lowpass(white(rng, n), 380, 3)) * env_exp(n, 0.06) * 0.9
    fsw = np.where(t < 0.05, 2000.0,
                   2000.0 * (60.0 / 2000.0) **
                   (np.clip((t - 0.05) / 1.15, 0.0, 1.0)))
    ph = phase_of(fsw)
    sw = (np.sin(ph) + 0.30 * np.sin(2 * ph + 0.8)) \
        * env_attack(n, 0.02) * np.exp(-t / 1.4)
    sw = taps(sw, [(0.130, 0.42), (0.260, 0.20), (0.390, 0.10)])  # fading delay
    mix = boom + thmp + 0.75 * sw
    return fade_io(sat(mix, 1.35), 0.003, 0.09)

def s_klaxon():
    T, n = 2.4, nsamp(2.4)
    t = taxis(n)
    seg = 0.3
    idx = np.minimum((t / seg).astype(int), 7)
    f = np.where(idx % 2 == 0, 620.0, 470.0)            # 620/470 alternation
    tone = np.tanh(2.6 * np.sin(phase_of(f))) / np.tanh(2.6)
    tone += 0.18 * np.sin(2 * phase_of(f))
    tone = lowpass(tone, 4200, 2)
    gate = np.ones(n)                                    # articulation dips
    for k in range(1, 8):
        b = nsamp(k * seg); w = nsamp(0.006)
        i0, i1 = max(b - w, 0), min(b + w, n)
        gate[i0:i1] *= 0.25 + 0.75 * np.abs(
            np.linspace(-1, 1, i1 - i0))
    tone *= gate * (1.0 + 0.06 * np.sin(2 * np.pi * 31.0 * t))
    tone = taps(tone, [(0.090, 0.24), (0.180, 0.10)])   # short echo
    return fade_io(tone, 0.005, 0.06)

def s_ultra_siren():
    T, n = 4.5, nsamp(4.5)
    t = taxis(n)
    rng = np.random.default_rng(115)
    period = 2.25                                        # 2 triangular cycles
    pos = (t % period) / period
    tri = np.where(pos < 0.5, 2.0 * pos, 2.0 - 2.0 * pos)
    f = 380.0 + 400.0 * tri
    ph = phase_of(f)
    tone = np.zeros(n)
    for k in range(1, 7):
        tone += np.sin(k * ph + 0.3 * k) / (k ** 1.25)
    growl = 1.0 + 0.11 * slow_noise(rng, n, 38)          # AM hoarseness
    tone *= growl
    wet = comb_chain(tone, [(0.149, 0.42), (0.233, 0.30), (0.371, 0.24)])
    mix = tone + 0.55 * (wet - tone)
    mix *= adsr(n, 0.05, 0.0, 1.0, 0.4)
    return fade_io(mix, 0.005, 0.30)

def _debris_clank(seed, f0, ratios, taus, amps, bounce=None):
    T, n = 0.9, nsamp(0.9)
    t = taxis(n)
    rng = np.random.default_rng(seed)
    def hit(t0, gain, detune):
        y = np.zeros(n)
        for r, tau, a in zip(ratios, taus, amps):
            fq = f0 * r * detune * (1.0 + rng.uniform(-0.004, 0.004))
            y += a * np.sin(2 * np.pi * fq * t + rng.uniform(0, 6.28)) \
                * env_exp(n, tau, t0)
        tr = white(rng, n) * env_exp(n, 0.0035, t0)
        y += 0.85 * highpass(tr, 1300, 2)
        return gain * y
    mix = hit(0.0, 1.0, 1.0)
    if bounce is not None:
        mix += hit(bounce, 0.42, 1.06)
    dirt = 1.0 + 0.08 * slow_noise(rng, n, 160)
    return fade_io(sat(mix * dirt, 1.35), 0.003, 0.07)

def s_debris_clank_0():
    return _debris_clank(116, 312.0,
                         (1.0, 1.74, 2.63, 3.92, 5.11),
                         (0.42, 0.31, 0.22, 0.15, 0.10),
                         (1.0, 0.72, 0.55, 0.38, 0.24))

def s_debris_clank_1():
    return _debris_clank(117, 524.0,
                         (1.0, 1.51, 2.26, 3.44, 4.83, 6.02),
                         (0.30, 0.24, 0.18, 0.13, 0.09, 0.06),
                         (1.0, 0.78, 0.60, 0.42, 0.28, 0.17))

def s_debris_clank_2():
    return _debris_clank(118, 236.0,
                         (1.0, 1.87, 2.79, 4.23, 5.64),
                         (0.50, 0.36, 0.26, 0.17, 0.11),
                         (1.0, 0.68, 0.52, 0.34, 0.21),
                         bounce=0.28)

def s_turret_servo():
    """Electric turret-rotation servo (seamless loop): 120 Hz DC motor with
    harmonics + 480 Hz gear mesh chewed by 8 Hz AM + a very subtle 2.4 kHz
    drive whine + commutator hash breathing with the mesh."""
    def render(m):
        t = taxis(m)
        rng = np.random.default_rng(120)
        load = slow_noise(rng, m, 1.8)                 # slow load wobble
        phm = phase_of(120.0 * (1.0 + 0.005 * load))   # DC motor fundamental
        motor = np.zeros(m)
        for k, a in ((1, 1.0), (2, 0.48), (3, 0.26), (4, 0.14), (6, 0.06)):
            motor += a * np.sin(k * phm + rng.uniform(0, 6.28))
        phg = phase_of(480.0 * (1.0 + 0.005 * load))   # gear mesh
        mesh_am = 0.55 + 0.45 * (0.5 + 0.5 * np.sin(2 * np.pi * 8.0 * t)) ** 1.4
        gear = (np.sin(phg) + 0.35 * np.sin(2.0 * phg + 0.9)) * mesh_am
        whine = 0.05 * np.sin(phase_of(2400.0 * (1.0 + 0.002 * load)))
        hash_ = norm(bandpass(white(rng, m), 900, 3600, 2)) \
            * (0.10 + 0.05 * mesh_am)
        return sat(0.95 * motor + 0.45 * gear + whine + hash_, 1.4)
    return make_loop(render, 1.6, 0.30)

def s_tank_tracks():
    """Tank tracks advancing (seamless loop): rhythmic metallic link clanks
    (~4/s with timing/pitch/level jitter) + deep ground rumble breathing
    with the rhythm + faint wandering road-wheel squeal + grind bed."""
    def render(m):
        t = taxis(m)
        rng = np.random.default_rng(121)
        Tm = m / SR
        out = np.zeros(m)
        # track-link clanks on a jittered ~4/s grid over the whole render;
        # the equal-power crossfade blends the (uncorrelated) head/tail
        # instances so the rhythm carries seamlessly across the wrap
        for k in range(int(np.ceil(4.0 * Tm))):
            tc = (k + rng.uniform(0.15, 0.85)) / 4.0
            i = nsamp(tc)
            if i >= m - 32:
                continue
            L = min(nsamp(0.22), m - i)
            tl = np.arange(L) / SR
            f0 = rng.uniform(230.0, 430.0)
            g = rng.uniform(0.45, 1.0)
            hit = np.zeros(L)
            for r, a in ((1.0, 1.0), (1.63, 0.62), (2.51, 0.40), (3.87, 0.22)):
                tau = rng.uniform(0.035, 0.075) / np.sqrt(r)
                hit += a * np.sin(2 * np.pi * f0 * r * tl
                                  + rng.uniform(0, 6.28)) * np.exp(-tl / tau)
            hit += 0.8 * rng.standard_normal(L) * np.exp(-tl / 0.004)
            e = min(64, L // 2)
            hit[:e] *= np.linspace(0.0, 1.0, e)
            out[i:i + L] += g * hit
        clank = bandpass(out, 300, 5200, 2)
        breathe = 1.0 + 0.22 * np.sin(2 * np.pi * 4.0 * t + 0.8) \
            + 0.10 * slow_noise(rng, m, 2.5)
        rum = norm(lowpass(brown(rng, m), 120, 3)) * 0.9 * breathe
        fsq = 1350.0 * (1.0 + 0.06 * slow_noise(rng, m, 0.9))
        squeal = np.sin(phase_of(fsq)) * (0.05 + 0.035 * slow_noise(rng, m, 1.6))
        grind = norm(bandpass(white(rng, m), 500, 2600, 2)) * 0.16
        return sat(0.8 * clank + rum + squeal + grind, 1.5)
    return make_loop(render, 2.2, 0.40)

def s_shell_casing():
    """Ejected brass casing: bright inharmonic PING (1.2k / 1.9k / 2.7 kHz
    partials, ~0.4 s ring) followed by a duller, lower double bounce."""
    T, n = 0.8, nsamp(0.8)
    t = taxis(n)
    rng = np.random.default_rng(122)
    modes = ((1200.0, 0.115, 1.00), (1900.0, 0.088, 0.74),
             (2700.0, 0.066, 0.55), (3960.0, 0.042, 0.30),
             (5330.0, 0.026, 0.16))
    def ping(t0, gain, det, damp):
        y = np.zeros(n)
        for fq, tau, a in modes:
            fq *= det * (1.0 + rng.uniform(-0.004, 0.004))
            y += a * np.sin(2 * np.pi * fq * t + rng.uniform(0, 6.28)) \
                * env_exp(n, tau * damp, t0)
        tick = highpass(white(rng, n) * env_exp(n, 0.0016, t0), 2600, 2)
        return gain * (y + 0.55 * tick)
    mix = ping(0.0, 1.0, 1.0, 1.0)          # primary ping, rings ~0.4 s
    mix += ping(0.27, 0.48, 0.86, 0.60)     # first bounce: lower, damped
    mix += ping(0.45, 0.24, 0.74, 0.42)     # second bounce: lower still
    ground = lowpass(white(rng, n), 900, 2) * (
        env_exp(n, 0.010, 0.27) + 0.7 * env_exp(n, 0.008, 0.45)) * 0.5
    return fade_io(mix + ground, 0.003, 0.06)

def s_crater_sizzle():
    """Smoking crater: scattered ember crackle thinning out over a steam
    hiss whose filter and level sink as the crater cools."""
    T, n = 2.5, nsamp(2.5)
    t = taxis(n)
    rng = np.random.default_rng(123)
    cool = np.clip(1.0 - t / T, 0.0, 1.0)
    crk = grains(rng, n, 150, 0.02, 2.42, 0.001, 0.0055,
                 lambda p: 0.30 + 0.70 * max(0.0, 1.0 - p / 2.45), bias=0.75)
    crk = bandpass(crk, 1400, 7800, 2)
    # descending filtered hiss: bright steam morphs into dull smoulder
    bright = norm(bandpass(white(rng, n), 900, 7000, 2))
    dark = norm(bandpass(white(rng, n), 220, 1900, 2))
    mixf = cool ** 1.3
    breath = np.clip(0.55 + 0.20 * slow_noise(rng, n, 2.8), 0.10, 1.0)
    bed = (bright * mixf + dark * (1.0 - 0.45 * mixf)) * breath \
        * (0.35 + 0.65 * cool ** 0.8)
    ember = 0.06 * cool * np.sin(phase_of(
        163.0 * (1.0 + 0.04 * slow_noise(rng, n, 1.2))))
    mix = 0.9 * crk + 0.75 * bed + ember
    return fade_io(mix, 0.012, 0.22)

# ----------------------------------------------------------------------------
# Build list, write with post-encode validation, retry until everything passes
# ----------------------------------------------------------------------------

SOUNDS = [
    ("explosion_near.ogg", 2.6, False, s_explosion_near),
    ("explosion_far.ogg",  3.2, False, s_explosion_far),
    ("shell_whistle.ogg",  2.2, False, s_shell_whistle),
    ("cannon_fire.ogg",    1.4, False, s_cannon_fire),
    ("tank_engine.ogg",    3.0, True,  s_tank_engine),
    ("tank_landing.ogg",   1.8, False, s_tank_landing),
    ("missile_launch.ogg", 2.8, False, s_missile_launch),
    ("missile_loop.ogg",   2.0, True,  s_missile_loop),
    ("mlrs_launch.ogg",    1.1, False, s_mlrs_launch),
    ("laser_charge.ogg",   4.2, False, s_laser_charge),
    ("laser_beam.ogg",     2.0, True,  s_laser_beam),
    ("warp_in.ogg",        1.6, False, s_warp_in),
    ("warp_out.ogg",       1.6, False, s_warp_out),
    ("klaxon.ogg",         2.4, False, s_klaxon),
    ("ultra_siren.ogg",    4.5, False, s_ultra_siren),
    ("debris_clank_0.ogg", 0.9, False, s_debris_clank_0),
    ("debris_clank_1.ogg", 0.9, False, s_debris_clank_1),
    ("debris_clank_2.ogg", 0.9, False, s_debris_clank_2),
    # ---- v2: anti-repetition variants ---------------------------------------
    ("explosion_near_1.ogg", 2.45, False, s_explosion_near_1),
    ("explosion_near_2.ogg", 2.75, False, s_explosion_near_2),
    ("shell_whistle_1.ogg", 2.0, False, s_shell_whistle_1),
    ("mlrs_launch_1.ogg",  1.0, False, s_mlrs_launch_1),
    # ---- v2: new events ------------------------------------------------------
    ("turret_servo.ogg",   1.6, True,  s_turret_servo),
    ("tank_tracks.ogg",    2.2, True,  s_tank_tracks),
    ("shell_casing.ogg",   0.8, False, s_shell_casing),
    ("crater_sizzle.ogg",  2.5, False, s_crater_sizzle),
]

def write_validated(name, x, dur, is_loop):
    """Write OGG; re-scale / re-pin and rewrite until decoded file passes."""
    path = os.path.join(OUT, name)
    n = nsamp(dur)
    assert len(x) == n, f"{name}: {len(x)} != {n}"
    target = 0.89
    x = x.astype(np.float64)
    for attempt in range(12):
        y = x * (target / (np.max(np.abs(x)) + 1e-12))
        y = np.clip(y, -0.985, 0.985)
        sf.write(path, y.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        d, _ = sf.read(path, dtype="float64")
        pk = float(np.max(np.abs(d)))
        ok_peak = 0.80 <= pk <= 0.90
        ok_loop = True
        if is_loop:
            ok_loop = abs(float(d[-1]) - float(d[0])) < 0.01
        if ok_peak and ok_loop:
            return
        if pk > 0.90 or pk < 0.80:
            target *= 0.885 / pk
        if not ok_loop:
            # converge both file endpoints to their mean over ~1.5 ms and
            # nudge the encode level so quantization luck changes (a pure
            # rewrite of identical data would fail deterministically forever)
            K = 64
            v = 0.5 * (x[0] + x[-1])
            r = np.linspace(0.0, 1.0, K)
            x[:K] = v + (x[:K] - v) * r
            x[-K:] = v + (x[-K:] - v) * r[::-1]
            target *= 0.988
    raise RuntimeError(f"{name}: could not satisfy constraints after retries")

def main():
    os.makedirs(OUT, exist_ok=True)
    for name, dur, is_loop, fn in SOUNDS:
        x = fn()
        write_validated(name, x, dur, is_loop)
        print(f"  rendered {name}")

    # ---- final validation pass ---------------------------------------------
    print()
    hdr = (f"{'file':<22} {'dur(s)':>7} {'target':>7} {'ch':>3} "
           f"{'peak':>7} {'loopdiff':>9} {'status':>7}")
    print(hdr)
    print("-" * len(hdr))
    all_ok = True
    for name, dur, is_loop, _ in SOUNDS:
        path = os.path.join(OUT, name)
        d, sr = sf.read(path, dtype="float64")
        ch = 1 if d.ndim == 1 else d.shape[1]
        got = len(d) / sr
        pk = float(np.max(np.abs(d)))
        ld = abs(float(d[-1]) - float(d[0])) if is_loop else None
        ok = (abs(got - dur) <= 0.1 and ch == 1 and 0.80 <= pk <= 0.90
              and (ld is None or ld < 0.01))
        all_ok &= ok
        lds = f"{ld:9.5f}" if ld is not None else "        -"
        print(f"{name:<22} {got:7.3f} {dur:7.2f} {ch:3d} "
              f"{pk:7.4f} {lds} {'PASS' if ok else 'FAIL':>7}")
    print("-" * len(hdr))
    print("ALL PASS" if all_ok else "FAILURES PRESENT")
    sys.exit(0 if all_ok else 1)

if __name__ == "__main__":
    main()
