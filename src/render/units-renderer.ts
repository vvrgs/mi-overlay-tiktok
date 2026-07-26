/**
 * Renderizado de las unidades.
 *
 * Tres ideas sostienen esto:
 *
 * 1. **Un grupo por (arquetipo × equipo)**, con un buffer intercalado que se
 *    sube tal cual llega del worker.
 * 2. **Animación por esqueleto en el vertex shader.** Cada vértice sabe su hueso
 *    y las dos articulaciones de su cadena, así que el shader puede doblar
 *    rodillas y codos —rotar primero sobre la articulación propia y después
 *    sobre la del padre— sin ningún sistema de huesos en CPU.
 * 3. **LOD por distancia.** Las instancias se reparten en cada frame entre una
 *    malla detallada (las cercanas) y una reducida (el resto). Es lo que permite
 *    subir el detalle de los modelos sin multiplicar el coste: en pantalla solo
 *    unas pocas docenas de unidades están lo bastante cerca para notarlo.
 *
 * Los colores salen en espacio lineal sin codificar: el paso final a sRGB lo
 * hace el post-procesado.
 */

import * as THREE from 'three';
import { UNIT_STRIDE, type UnitGroup } from '../sim/protocol';
import { NOISE_2D } from './shader-chunks';
import { createArchetypeGeometry, createBlobShadowGeometry, type Detail } from './unit-geometry';

const UNIT_VERTEX_SHADER = /* glsl */ `
  attribute float aLimb;
  attribute vec3 aPivot;
  attribute vec3 aPivot2;
  attribute float aShade;
  attribute float aOcc;
  attribute vec2 aUv;

  attribute vec3 aOffset;
  attribute float aRot;
  attribute float aScale;
  attribute float aPhase;
  attribute float aState;
  attribute float aHealth;

  varying vec3 vNormal;
  varying float vShade;
  varying float vHealth;
  varying float vDeath;
  varying float vFogDepth;
  varying float vRandom;
  varying float vOcc;
  varying vec3 vViewDir;
  varying vec2 vUv;
  varying float vDist;

  vec3 rotX(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c); }
  vec3 rotY(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }
  vec3 rotZ(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x * c - p.y * s, p.x * s + p.y * c, p.z); }

  uniform float uTime;
  uniform vec2 uWind;

  void main() {
    // aState codifica el estado: 0..1 marcha (valor = ritmo), 1..2 ataque, 2..3 caída.
    float mode = floor(aState);
    float sub = fract(aState);
    float walk = mode < 0.5 ? sub : 0.22;
    float strike = (mode > 0.5 && mode < 1.5) ? sub : 0.0;
    float death = mode > 1.5 ? sub : 0.0;

    // Semilla estable por instancia: rompe la uniformidad del ejército.
    vRandom = fract(sin(aPhase * 12.9898 + aOffset.x * 0.137 + aOffset.z * 0.531) * 43758.5453);

    float t = aPhase * 3.0;
    // Zancada. Un ángulo positivo lleva el miembro hacia ATRÁS (el modelo mira a +Z).
    float stride = sin(t) * (0.22 + walk * 1.0);
    float armSwing = stride * 0.55;

    // Estilo de ataque por instancia: unos tiran un tajo alto, otros una estocada
    // y otros un revés. Con un solo golpe para todos, mil soldados se mueven como
    // un único muñeco y el ojo lo detecta enseguida.
    float style = floor(vRandom * 3.0);
    float punch;
    float punchLift = 0.0;
    if (style < 1.0) {
      // Tajo descendente: el brazo sube y cae.
      punch = -2.4 * strike;
      punchLift = sin(strike * 3.14159) * 0.8;
    } else if (style < 2.0) {
      // Estocada: brazo casi recto hacia adelante.
      punch = -1.5 * strike;
    } else {
      // Revés: cruza el cuerpo.
      punch = -1.9 * strike;
    }

    // Guardia: entre golpe y golpe el arma no vuelve del todo al costado.
    float guard = (mode > 0.5 && mode < 1.5) ? 0.45 : 0.0;

    // Arquería: el brazo del arco apunta al frente y el otro tensa la cuerda.
    // Se calculan aquí porque el arco (que no rota) necesita el MISMO ángulo que
    // el brazo para saber dónde acaba la mano.
    float bowAim = -1.22 - strike * 0.1 + armSwing * 0.12;
    float drawPull = smoothstep(0.0, 0.7, strike) - smoothstep(0.76, 0.9, strike);

    float angle = 0.0;   // rotación sobre la articulación propia
    float parent = 0.0;  // rotación sobre la articulación del padre
    float yaw = 0.0;

    if (aLimb == 1.0) {
      angle = stride;
    } else if (aLimb == 2.0) {
      angle = -stride;
    } else if (aLimb == 8.0) {
      // Rodilla: solo dobla hacia atrás, y sobre todo al despegar el pie.
      angle = max(0.0, stride) * 1.5 + 0.06;
      parent = stride;
    } else if (aLimb == 9.0) {
      angle = max(0.0, -stride) * 1.5 + 0.06;
      parent = -stride;
    } else if (aLimb == 3.0) {
      // Brazo del escudo: se alza a cubrirse cuando la unidad está peleando.
      angle = -armSwing - guard * 0.7;
    } else if (aLimb == 4.0) {
      angle = armSwing + punch - guard * 0.5;
      yaw = (style > 1.5) ? -strike * 0.5 : 0.0;
    } else if (aLimb == 10.0) {
      angle = 0.34 + max(0.0, armSwing) * 0.6 + guard * 0.9;
      parent = -armSwing - guard * 0.7;
    } else if (aLimb == 11.0) {
      // El codo derecho se extiende en el impacto y se recoge al volver.
      angle = 0.4 - 0.34 * strike + max(0.0, -armSwing) * 0.6 + guard * 0.6 + punchLift;
      parent = armSwing + punch - guard * 0.5;
    } else if (aLimb == 5.0) {
      // La cabeza compensa el giro del torso: mira al frente aunque el cuerpo rote.
      yaw = stride * 0.14;
      angle = -walk * 0.1;
    } else if (aLimb == 0.0) {
      // Contrarrotación del torso: es lo que da naturalidad al caminar.
      yaw = -stride * 0.12;
      angle = strike * -0.16;
    } else if (aLimb == 12.0) {
      yaw = stride * 0.16;
    } else if (aLimb == 13.0 || aLimb == 16.0) {
      // Brazo del arco: extendido al frente y sostenido ahí. Apenas acompaña la
      // marcha, porque un arquero no bracea con el arco: lo lleva firme.
      angle = bowAim;
      yaw = -0.3;
    } else if (aLimb == 14.0) {
      // Hombro del brazo que tensa: sube a la altura del arco.
      angle = -1.05 + armSwing * 0.1;
      yaw = 0.28;
    } else if (aLimb == 15.0) {
      // Antebrazo: se pliega hacia la mejilla al tensar y se suelta de golpe.
      // El tirón es lento (la cuerda cuesta) y la suelta instantánea.
      angle = 1.5 + drawPull * 0.5;
      parent = -1.05 + armSwing * 0.1;
    }

    vec3 local = position;
    vec3 nrm = normal;

    if (aLimb == 6.0) {
      // Capas, alas y colas: ondean sobre Z y se abren hacia atrás con la marcha.
      float windPush = length(uWind);
      float flap = sin(t * 3.0 + vRandom * 3.0 + uTime * (1.5 + windPush * 3.0)) * (0.3 + walk * 0.45 + windPush * 0.5) * sign(local.x + 0.0001);
      vec3 rel = local - aPivot;
      local = aPivot + rotZ(rel, flap);
      nrm = rotZ(nrm, flap);
      // El viento arrastra la tela; la marcha la abre hacia atrás.
      local.z -= walk * 0.16;
      float cloth = max(0.0, aPivot.y - position.y) * 0.6;
      local.x += uWind.x * cloth;
      local.z += uWind.y * cloth;
    } else if (aLimb == 16.0) {
      // El arco NO rota: se queda vertical y solo se traslada a donde acabe la
      // mano del brazo que lo sostiene. Aquí aPivot2 guarda la posición de esa
      // mano en reposo, no la articulación del padre.
      vec3 hand = aPivot + rotX(aPivot2 - aPivot, angle);
      hand = aPivot + rotY(hand - aPivot, yaw);
      local += hand - aPivot2;
    } else if (aLimb != 7.0) {
      // Primero la articulación propia (rodilla, codo, cuello)...
      vec3 rel = local - aPivot;
      local = aPivot + rotX(rel, angle);
      nrm = rotX(nrm, angle);
      // ...y después la del padre (cadera, hombro). Ese orden es el que hace
      // que la pantorrilla siga al muslo en vez de girar por su cuenta.
      if (parent != 0.0) {
        vec3 rel2 = local - aPivot2;
        local = aPivot2 + rotX(rel2, parent);
        nrm = rotX(nrm, parent);
      }
      if (yaw != 0.0) {
        vec3 rel3 = local - aPivot;
        local = aPivot + rotY(rel3, yaw);
        nrm = rotY(nrm, yaw);
      }
    }

    // Rebote vertical del paso.
    local.y += abs(sin(t)) * 0.045 * walk;
    // Inclinación hacia adelante al correr: el cuerpo persigue su centro de masa.
    if (walk > 0.01) {
      local = rotX(local, -walk * 0.11);
      nrm = rotX(nrm, -walk * 0.11);
    }

    // Caída, con tres variantes: de bruces, de espaldas o desplomándose de
    // rodillas. Que todos caigan igual delata la copia tanto como el ataque.
    if (death > 0.0) {
      float fall = death * death;
      float dstyle = floor(vRandom * 3.0);
      float dir = dstyle < 1.0 ? 1.55 : dstyle < 2.0 ? -1.5 : 0.9;
      local = rotX(local, fall * dir);
      nrm = rotX(nrm, fall * dir);
      // El desplome también gira sobre sí mismo al desmadejarse.
      float twist = (vRandom - 0.5) * fall * 1.1;
      local = rotY(local, twist);
      nrm = rotY(nrm, twist);
      local.y -= fall * (dstyle < 2.0 ? 0.2 : 0.55);
    }

    // Ligera variación de estatura: dos soldados idénticos delatan la copia.
    local *= aScale * (0.94 + vRandom * 0.12);
    local = rotY(local, aRot);
    nrm = rotY(nrm, aRot);

    vec3 world = local + aOffset;
    vec4 mv = modelViewMatrix * vec4(world, 1.0);

    vNormal = nrm;
    vShade = aShade;
    vHealth = aHealth;
    vDeath = death;
    vOcc = aOcc;
    vUv = aUv;
    vDist = -mv.z;
    vFogDepth = -mv.z;
    vViewDir = normalize(-mv.xyz);
    gl_Position = projectionMatrix * mv;
  }
`;

const UNIT_FRAGMENT_SHADER = /* glsl */ `
  precision highp float;
${NOISE_2D}

  uniform vec3 uTeamColor;
  uniform vec3 uTeamColorDark;
  uniform vec3 uTeamColorLight;
  uniform vec3 uSkinColor;
  uniform vec3 uMetalColor;
  uniform vec3 uLeatherColor;
  uniform vec3 uGlowColor;
  uniform float uGlowStrength;
  uniform vec3 uLightDir;
  uniform vec3 uSunColor;
  uniform vec3 uSkyColor;
  uniform vec3 uGroundColor;
  uniform vec3 uFogColor;
  uniform float uFogDensity;
  uniform float uTextureDistance;
  /** 0 = seco, 1 = empapado. Oscurece y da brillo, como la ropa mojada. */
  uniform float uWetness;
  /** Nieve acumulada en hombros y cascos. */
  uniform float uSnow;

  varying vec3 vNormal;
  varying float vShade;
  varying float vHealth;
  varying float vDeath;
  varying float vFogDepth;
  varying float vRandom;
  varying float vOcc;
  varying vec3 vViewDir;
  varying vec2 vUv;
  varying float vDist;

  // ---------------------------------------------------------- materiales
  // Todos los patrones son procedurales: no hay ni un archivo de imagen, así que
  // no hay atlas que cargar, ni límite de resolución, ni coste de memoria.

  /** Tejido: urdimbre y trama cruzadas, con pliegues de baja frecuencia. */
  float clothPattern(vec2 uv) {
    float weave = sin(uv.x * 110.0) * sin(uv.y * 110.0);
    float folds = fbm2(uv * 9.0, 3);
    return 0.9 + weave * 0.06 + (folds - 0.5) * 0.22;
  }

  /** Cota de malla: filas de anillos desplazadas media celda, como la real. */
  float chainPattern(vec2 uv) {
    vec2 cell = vec2(uv.x * 58.0, uv.y * 58.0);
    // Las filas impares se desplazan: sin eso parece una rejilla, no una malla.
    cell.x += step(1.0, mod(floor(cell.y), 2.0)) * 0.5;
    vec2 f = fract(cell) - 0.5;
    float ring = abs(length(f) - 0.34);
    return 0.62 + smoothstep(0.14, 0.02, ring) * 0.5;
  }

  /** Placa: rayado de forja en una dirección más desgaste en los cantos. */
  float platePattern(vec2 uv) {
    float brushed = sin(uv.x * 180.0 + fbm2(uv * 24.0, 2) * 6.0) * 0.045;
    float wear = fbm2(uv * 14.0, 3);
    return 0.94 + brushed + (wear - 0.5) * 0.18;
  }

  /** Cuero: celdas irregulares separadas por grietas oscuras. */
  float leatherPattern(vec2 uv) {
    float grain = fbm2(uv * 42.0, 3);
    float cracks = smoothstep(0.42, 0.5, fbm2(uv * 18.0, 2));
    return 0.86 + grain * 0.24 - cracks * 0.22;
  }

  /** Madera: vetas estiradas a lo largo del asta. */
  float woodPattern(vec2 uv) {
    float rings = fract(fbm2(vec2(uv.x * 30.0, uv.y * 3.0), 3) * 7.0);
    return 0.88 + rings * 0.2;
  }

  /**
   * Emblema del escudo. Cuatro diseños repartidos por instancia: banda, cruz,
   * chevrón y cuartelado. Es lo que hace que una formación no parezca una fila
   * de tablas idénticas.
   */
  vec3 heraldry(vec2 uv, vec3 field, vec3 charge) {
    // Coordenadas normalizadas del escudo, centradas.
    vec2 p = vec2(uv.x * 3.0, (uv.y - 1.05) * 2.2);
    float emblem = floor(vRandom * 4.0);
    float mask = 0.0;
    if (emblem < 1.0) mask = step(abs(p.y + p.x * 0.35), 0.22);                   // banda
    else if (emblem < 2.0) mask = max(step(abs(p.x), 0.16), step(abs(p.y), 0.16)); // cruz
    else if (emblem < 3.0) mask = step(abs(abs(p.x) - p.y - 0.1), 0.2);            // chevrón
    else mask = step(0.0, p.x * p.y);                                             // cuartelado
    vec3 color = mix(field, charge, mask);
    // Ribete oscuro en el borde del escudo.
    float border = smoothstep(0.62, 0.72, max(abs(p.x), abs(p.y) * 0.8));
    return mix(color, field * 0.45, border);
  }

  /**
   * Un único punto de entrada al patrón de la pieza. Existe para poder evaluarlo
   * tres veces —en uv y en dos vecinos— y sacar de ahí un relieve: sin relieve
   * cada polígono es perfectamente plano y por muy bien texturado que esté sigue
   * leyéndose como un bloque de plástico.
   */
  float materialPattern(vec2 uv, float shade, float seed) {
    if (shade > 6.5) return clothPattern(uv);
    if (shade > 5.5) return chainPattern(uv);
    if (shade > 4.5) return clothPattern(uv);
    if (shade > 3.5) return mix(leatherPattern(uv), woodPattern(uv), step(0.5, fract(seed * 7.0)));
    if (shade > 1.5) return platePattern(uv);
    if (shade < 0.5) return 0.96 + fbm2(uv * 60.0, 2) * 0.08;
    return clothPattern(uv);
  }

  /**
   * Altura del material para el relieve. NO es el mismo campo que el color: la
   * trama del tejido va a 110 ciclos por unidad y a la distancia de juego cae muy
   * por debajo del píxel, así que derivarla produce moiré, no volumen. Aquí solo
   * entran los rasgos GRUESOS —pliegues, celdas del cuero, abolladuras— que son
   * los que de verdad recogen la luz.
   */
  float materialHeight(vec2 uv, float shade) {
    if (shade > 6.5) return fbm2(uv * 8.0, 3);           // pliegues de la prenda
    if (shade > 5.5) return chainPattern(uv);            // los anillos sí son bulto real
    if (shade > 3.5 && shade < 4.5) return fbm2(uv * 16.0, 2);
    if (shade > 1.5 && shade < 2.5) return fbm2(uv * 12.0, 3);
    if (shade < 0.5) return fbm2(uv * 20.0, 2);
    return fbm2(uv * 8.0, 3);                            // pliegues de la tela
  }

  /** Fuerza del relieve por material. La malla abulta; el acero pulido casi no. */
  float reliefStrength(float shade) {
    if (shade > 6.5) return 1.0;    // prenda de faena
    if (shade > 5.5) return 0.5;    // cota de malla
    if (shade > 4.5) return 0.9;    // tela con heráldica
    if (shade > 3.5) return 1.0;    // cuero y madera
    if (shade > 1.5) return 0.55;   // placa abollada
    if (shade < 0.5) return 0.35;   // piel
    return 1.0;                     // tela
  }

  void main() {
    vec3 n = normalize(vNormal);

    // Color y acabado según el material de la pieza.
    vec3 base = uTeamColor;
    float gloss = 0.0;
    bool emissive = false;
    if (vShade < 0.5) { base = uSkinColor; gloss = 0.12; }
    else if (vShade > 6.5) {                                                  // prenda de faena
      base = mix(uTeamColorDark, vec3(0.2, 0.185, 0.17), 0.55);
      gloss = 0.0;
    }
    else if (vShade > 5.5) { base = uMetalColor * 0.86; gloss = 0.8; }        // cota de malla
    else if (vShade > 4.5) { base = uTeamColor; gloss = 0.05; }               // heráldica
    else if (vShade > 3.5) { base = uLeatherColor; gloss = 0.14; }            // cuero/madera
    else if (vShade > 2.5) { base = uGlowColor; emissive = true; }
    else if (vShade > 1.5) { base = uMetalColor; gloss = 1.0; }

    float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * vFogDepth * vFogDepth);

    if (emissive) {
      vec3 glow = base * uGlowStrength * (1.0 - vDeath * 0.9);
      gl_FragColor = vec4(mix(glow, uFogColor, clamp(fogFactor, 0.0, 1.0)), 1.0);
      return;
    }

    // Variación por instancia. El tono de piel varía de verdad —una multitud
    // monocroma no existe— y el cuero y el metal cambian de matiz por unidad.
    float tint = vRandom;
    if (vShade < 0.5) {
      base *= mix(vec3(0.62, 0.52, 0.46), vec3(1.14, 1.06, 1.0), tint);
    } else if (vShade > 3.5 && vShade < 4.5) {
      base *= mix(vec3(0.72, 0.66, 0.6), vec3(1.2, 1.1, 0.92), tint);
    } else if (vShade > 1.5 && vShade < 2.5) {
      base *= mix(vec3(0.86, 0.88, 0.94), vec3(1.1, 1.06, 0.98), tint);
    } else if (vShade > 6.5) {
      // La ropa de faena de cada uno está lavada y gastada de forma distinta.
      base *= mix(vec3(0.72, 0.7, 0.68), vec3(1.22, 1.18, 1.12), tint);
    } else {
      base *= 0.88 + tint * 0.24;
    }

    // El detalle de material solo se calcula cerca. De lejos ocupa menos de un
    // píxel: pagarlo sería tirar relleno para producir ruido.
    float cavity = 1.0;
    if (vDist < uTextureDistance) {
      float detail = smoothstep(uTextureDistance, uTextureDistance * 0.55, vDist);
      if (vShade > 4.5 && vShade < 5.5) {
        base = heraldry(vUv, base, mix(uTeamColorLight, vec3(0.92, 0.88, 0.8), step(2.0, floor(vRandom * 4.0))));
      }
      // La cota de malla tiene la celda más fina de todos los materiales: mucho
      // antes de llegar al límite general de textura ya cae por debajo del píxel
      // y se convierte en sal y pimienta. Se apaga en su propia distancia.
      float fade = detail;
      if (vShade > 5.5 && vShade < 6.5) {
        fade *= smoothstep(uTextureDistance * 0.34, uTextureDistance * 0.14, vDist);
      }
      float pattern = materialPattern(vUv, vShade, vRandom);
      base *= mix(1.0, pattern, fade);

      // Relieve: se estima la pendiente del campo de altura en UV y se dobla la
      // normal. No hay tangentes en la malla, así que se improvisa una base
      // ortogonal a partir de la propia normal; para un relieve fino sobra.
      float relief = reliefStrength(vShade) * fade;
      if (relief > 0.01) {
        const float E = 0.004;
        float h = materialHeight(vUv, vShade);
        float du = materialHeight(vUv + vec2(E, 0.0), vShade) - h;
        float dv = materialHeight(vUv + vec2(0.0, E), vShade) - h;
        vec3 tangent = normalize(cross(n, vec3(0.0, 1.0, 0.0)) + vec3(0.001, 0.0, 0.001));
        vec3 bitangent = cross(n, tangent);
        n = normalize(n - (tangent * du + bitangent * dv) * relief * 2.6);
        // Los valles reciben menos luz rebotada: es lo que hace que un pliegue se
        // vea hundido en vez de pintado encima.
        cavity = mix(1.0, clamp(0.62 + h * 0.62, 0.4, 1.15), relief);
      }

      // Suciedad: se acumula de las rodillas hacia abajo, más en las botas.
      float mud = smoothstep(0.45, 0.0, vUv.y) * (0.25 + fbm2(vUv * 6.0, 2) * 0.4);
      base = mix(base, vec3(0.21, 0.16, 0.12), clamp(mud * 0.4, 0.0, 0.34) * detail);
    }

    // Oclusión horneada: oscurece axilas, entrepierna y bajo las hombreras. Es
    // lo que da sensación de volumen sin calcular sombras propias.
    base *= mix(0.42, 1.0, vOcc);

    // Heridas: la unidad se oscurece y enrojece conforme pierde vida.
    base = mix(mix(uTeamColorDark, vec3(0.32, 0.05, 0.05), 0.45), base, clamp(vHealth, 0.0, 1.0) * 0.75 + 0.25);
    base *= 1.0 - vDeath * 0.55;

    // Mojado: la tela empapada se oscurece y refleja más.
    base *= 1.0 - uWetness * 0.28;
    gloss = min(1.0, gloss + uWetness * 0.55);

    // Iluminación "half-Lambert": las caras que no miran al sol se atenúan, pero
    // nunca llegan a negro. Con Lambert puro, media unidad quedaba en sombra dura
    // y el equipo rojo se leía como marrón oscuro en pantalla.
    vec3 lightDir = normalize(uLightDir);
    vec3 viewDir = normalize(vViewDir);
    float ndl = dot(n, lightDir);
    // Terminador con algo de nervio: un half-Lambert plano reparte la luz por
    // igual y aplana el volumen. Aquí la transición de luz a sombra es más corta
    // y el lado en sombra se sostiene con el rebote del cielo, no con luz falsa.
    float diffuse = pow(clamp(ndl * 0.62 + 0.38, 0.0, 1.0), 1.35);
    float hemi = n.y * 0.5 + 0.5;
    vec3 ambient = mix(uGroundColor, uSkyColor, hemi);
    // El ambiente del cielo es muy azul y apagaba al equipo rojo hasta dejarlo
    // marrón. Se desatura hacia el gris antes de multiplicar.
    float lum = dot(ambient, vec3(0.299, 0.587, 0.114));
    ambient = mix(ambient, vec3(lum), 0.7);
    vec3 lit = base * (ambient * 0.55 * cavity + uSunColor * diffuse * 0.95 * cavity);

    // Piel: en el borde del terminador la luz atraviesa la carne y se vuelve
    // rojiza. Sin esto una cara es una máscara de plástico.
    if (vShade < 0.5) {
      float sss = pow(clamp(1.0 - abs(ndl), 0.0, 1.0), 3.0);
      lit += base * vec3(0.5, 0.15, 0.1) * sss * 0.3;
    }

    // Reflejo especular proporcional al acabado: el metal destella al girar la
    // cámara, el cuero apenas y la tela nada.
    if (gloss > 0.0) {
      vec3 halfway = normalize(lightDir + viewDir);
      float spec = pow(max(dot(n, halfway), 0.0), mix(18.0, 58.0, gloss));
      lit += uSunColor * spec * gloss * 0.85 * (1.0 - vDeath) * vOcc;
    } else {
      // Brillo de tela: la ropa no destella, pero al sesgo la fibra sí devuelve
      // un halo suave. Es barato y quita mucho del aspecto de plástico mate.
      float sheen = pow(1.0 - max(dot(n, viewDir), 0.0), 2.5) * max(ndl, 0.0);
      lit += uSunColor * sheen * 0.16 * vOcc;
    }

    // Nieve posada: solo en las superficies que miran al cielo.
    if (uSnow > 0.0) {
      float up = smoothstep(0.35, 0.85, n.y);
      lit = mix(lit, vec3(0.92, 0.95, 1.0) * (0.6 + diffuse * 0.5), up * uSnow * vOcc);
    }

    // Borde iluminado por el cielo: despega la silueta del fondo.
    float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
    lit += uSkyColor * rim * 0.2 * vOcc;

    // Suelo de color propio: a 60 metros de cámara, lo único que importa es
    // distinguir de un vistazo quién es rojo y quién es azul. Pero cuanto más
    // cerca está la unidad, menos hace falta esa muleta y más estorba —aplana
    // todo el modelado—, así que se desvanece con la distancia.
    float flatten = 0.26 * smoothstep(uTextureDistance * 0.45, uTextureDistance * 1.6, vDist);
    vec3 color = mix(lit, base * 0.5, flatten);
    color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));

    gl_FragColor = vec4(color, 1.0);
  }
`;

const SHADOW_VERTEX_SHADER = /* glsl */ `
  attribute vec3 aOffset;
  attribute float aScale;
  attribute float aState;
  uniform vec2 uLightOffset;
  varying float vAlpha;
  varying vec2 vLocal;

  void main() {
    float mode = floor(aState);
    float death = mode > 1.5 ? fract(aState) : 0.0;
    vAlpha = 1.0 - death * 0.8;
    vLocal = position.xz;
    // La sombra se desplaza en el sentido contrario al sol, no bajo los pies.
    vec3 world = position * aScale * 1.25
               + vec3(aOffset.x + uLightOffset.x * aScale, aOffset.y + 0.04, aOffset.z + uLightOffset.y * aScale);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(world, 1.0);
  }
`;

const SHADOW_FRAGMENT_SHADER = /* glsl */ `
  precision mediump float;
  uniform float uOpacity;
  varying float vAlpha;
  varying vec2 vLocal;
  void main() {
    // Borde difuminado: una elipse dura se ve como una calcomanía pegada.
    float d = length(vLocal) / 0.42;
    gl_FragColor = vec4(0.0, 0.0, 0.0, uOpacity * vAlpha * smoothstep(1.0, 0.2, d));
  }
`;

export interface ArchetypeVisual {
  key: string;
  mesh: string;
  /** Las unidades de masa necesitan buffers grandes; las élite, no. */
  stackable: boolean;
}

export interface UnitsRendererOptions {
  capacityPerGroup: number;
  detail: Detail;
  archetypes: ArchetypeVisual[];
  teamColors: Record<'red' | 'blue', { color: THREE.Color; dark: THREE.Color }>;
  shadows: boolean;
  fogColor: THREE.Color;
  fogDensity: number;
  /** Distancia a partir de la cual una unidad usa la malla reducida. */
  lodDistance: number;
  /** Distancia hasta la que se calcula el detalle de material del shader. */
  textureDistance: number;
}

/** Capacidad de los grupos de unidades élite (gigantes, dragones, campeones). */
const ELITE_CAPACITY = 768;

interface Layer {
  geometry: THREE.InstancedBufferGeometry;
  buffer: THREE.InstancedInterleavedBuffer;
  array: Float32Array;
  mesh: THREE.Mesh;
}

interface GroupRuntime {
  capacity: number;
  near: Layer;
  far: Layer;
  /** Buffer con TODAS las instancias; solo lo usa la sombra. */
  all: Layer | null;
}

export class UnitsRenderer {
  readonly root = new THREE.Group();
  private groups: Array<GroupRuntime | null> = [];
  private materials: THREE.ShaderMaterial[] = [];
  private materialsByTeam: Record<'red' | 'blue', THREE.ShaderMaterial[]> = { red: [], blue: [] };
  private shadowMaterial: THREE.ShaderMaterial;
  private baseGeometries: THREE.BufferGeometry[] = [];
  private shadowGeometry = createBlobShadowGeometry();
  private lodDistanceSq: number;

  constructor(private options: UnitsRendererOptions) {
    this.lodDistanceSq = options.lodDistance * options.lodDistance;
    this.shadowMaterial = new THREE.ShaderMaterial({
      vertexShader: SHADOW_VERTEX_SHADER,
      fragmentShader: SHADOW_FRAGMENT_SHADER,
      uniforms: {
        uOpacity: { value: options.shadows ? 0.34 : 0.0 },
        uLightOffset: { value: new THREE.Vector2(-0.25, -0.2) },
      },
      transparent: true,
      depthWrite: false,
      polygonOffset: true,
      polygonOffsetFactor: -2,
    });

    options.archetypes.forEach((archetype, index) => {
      // En calidad baja no hay LOD: una sola silueta simplificada para todo.
      const near = createArchetypeGeometry(archetype.key, archetype.mesh, options.detail);
      const far = options.detail === 'high' ? createArchetypeGeometry(archetype.key, archetype.mesh, 'low') : near;
      this.baseGeometries.push(near);
      if (far !== near) this.baseGeometries.push(far);
      this.createGroup(index, 'red', near, far);
      this.createGroup(index, 'blue', near, far);
    });
    this.root.frustumCulled = false;
  }

  /** Índice plano de un grupo dentro del array: arquetipo × 2 + equipo. */
  private slot(archetype: number, team: 0 | 1 | 'red' | 'blue'): number {
    const teamIndex = team === 'red' ? 0 : team === 'blue' ? 1 : team;
    return archetype * 2 + teamIndex;
  }

  private makeLayer(
    base: THREE.BufferGeometry,
    capacity: number,
    material: THREE.Material,
    renderOrder: number,
    animated: boolean,
  ): Layer {
    const array = new Float32Array(capacity * UNIT_STRIDE);
    const buffer = new THREE.InstancedInterleavedBuffer(array, UNIT_STRIDE);
    buffer.setUsage(THREE.DynamicDrawUsage);

    const geometry = new THREE.InstancedBufferGeometry();
    geometry.setIndex(base.index);
    geometry.setAttribute('position', base.getAttribute('position'));
    geometry.setAttribute('aOffset', new THREE.InterleavedBufferAttribute(buffer, 3, 0));
    geometry.setAttribute('aScale', new THREE.InterleavedBufferAttribute(buffer, 1, 4));
    geometry.setAttribute('aState', new THREE.InterleavedBufferAttribute(buffer, 1, 6));
    // La sombra es un disco plano: no necesita normales, huesos ni vida.
    if (animated) {
      geometry.setAttribute('normal', base.getAttribute('normal'));
      geometry.setAttribute('aLimb', base.getAttribute('aLimb'));
      geometry.setAttribute('aPivot', base.getAttribute('aPivot'));
      geometry.setAttribute('aPivot2', base.getAttribute('aPivot2'));
      geometry.setAttribute('aShade', base.getAttribute('aShade'));
      geometry.setAttribute('aOcc', base.getAttribute('aOcc'));
      geometry.setAttribute('aUv', base.getAttribute('aUv'));
      geometry.setAttribute('aRot', new THREE.InterleavedBufferAttribute(buffer, 1, 3));
      geometry.setAttribute('aPhase', new THREE.InterleavedBufferAttribute(buffer, 1, 5));
      geometry.setAttribute('aHealth', new THREE.InterleavedBufferAttribute(buffer, 1, 7));
    }
    geometry.instanceCount = 0;
    geometry.boundingSphere = new THREE.Sphere(new THREE.Vector3(), 1e6);

    const mesh = new THREE.Mesh(geometry, material);
    mesh.frustumCulled = false;
    mesh.renderOrder = renderOrder;
    this.root.add(mesh);
    return { geometry, buffer, array, mesh };
  }

  private createGroup(
    archetype: number,
    teamName: 'red' | 'blue',
    nearBase: THREE.BufferGeometry,
    farBase: THREE.BufferGeometry,
  ): void {
    const visual = this.options.archetypes[archetype];
    const capacity = visual.stackable ? this.options.capacityPerGroup : ELITE_CAPACITY;
    const teamColor = this.options.teamColors[teamName];

    const material = new THREE.ShaderMaterial({
      vertexShader: UNIT_VERTEX_SHADER,
      fragmentShader: UNIT_FRAGMENT_SHADER,
      uniforms: {
        uTeamColor: { value: teamColor.color.clone() },
        uTeamColorDark: { value: teamColor.dark.clone() },
        uTeamColorLight: { value: teamColor.color.clone().lerp(new THREE.Color('#ffffff'), 0.55) },
        uSkinColor: { value: new THREE.Color('#b0846a') },
        uMetalColor: { value: new THREE.Color('#a4acb8') },
        uLeatherColor: { value: new THREE.Color('#6b4a2c') },
        uGlowColor: { value: teamColor.color.clone().lerp(new THREE.Color('#ffffff'), 0.45) },
        uGlowStrength: { value: 1.9 },
        uLightDir: { value: new THREE.Vector3(0.45, 0.82, 0.35).normalize() },
        uSunColor: { value: new THREE.Color('#fff2d8') },
        uSkyColor: { value: new THREE.Color('#8fb6e8') },
        uGroundColor: { value: new THREE.Color('#4a4030') },
        uFogColor: { value: this.options.fogColor.clone() },
        uFogDensity: { value: this.options.fogDensity },
        uTime: { value: 0 },
        uWind: { value: new THREE.Vector2() },
        uTextureDistance: { value: this.options.textureDistance },
        uWetness: { value: 0 },
        uSnow: { value: 0 },
      },
    });
    this.materials.push(material);
    this.materialsByTeam[teamName].push(material);

    const near = this.makeLayer(nearBase, capacity, material, 2, true);
    const far = this.makeLayer(farBase, capacity, material, 2, true);

    // Los que vuelan no llevan sombra de contacto: se vería flotando con ellos.
    let all: Layer | null = null;
    if (visual.mesh !== 'dragon') {
      all = this.makeLayer(this.shadowGeometry, capacity, this.shadowMaterial, 1, false);
    }

    this.groups[this.slot(archetype, teamName)] = { capacity, near, far, all };
  }

  /**
   * Sube el snapshot del worker a la GPU, repartiendo cada instancia entre la
   * malla detallada y la reducida según su distancia a la cámara.
   */
  update(source: Float32Array, groups: UnitGroup[], cameraPosition: THREE.Vector3): void {
    for (const runtime of this.groups) {
      if (!runtime) continue;
      runtime.near.geometry.instanceCount = 0;
      runtime.far.geometry.instanceCount = 0;
      if (runtime.all) runtime.all.geometry.instanceCount = 0;
    }

    const cx = cameraPosition.x;
    const cy = cameraPosition.y;
    const cz = cameraPosition.z;

    for (const { archetype, team, start, count } of groups) {
      const runtime = this.groups[this.slot(archetype, team)];
      if (!runtime) continue;
      const total = Math.min(count, runtime.capacity);
      if (total <= 0) continue;

      const nearArray = runtime.near.array;
      const farArray = runtime.far.array;
      let nearCount = 0;
      let farCount = 0;

      for (let i = 0; i < total; i++) {
        const src = (start + i) * UNIT_STRIDE;
        const dx = source[src] - cx;
        const dy = source[src + 1] - cy;
        const dz = source[src + 2] - cz;
        const near = dx * dx + dy * dy + dz * dz <= this.lodDistanceSq;
        const target = near ? nearArray : farArray;
        const dst = (near ? nearCount++ : farCount++) * UNIT_STRIDE;
        for (let k = 0; k < UNIT_STRIDE; k++) target[dst + k] = source[src + k];
      }

      runtime.near.geometry.instanceCount = nearCount;
      runtime.far.geometry.instanceCount = farCount;
      if (nearCount > 0) runtime.near.buffer.needsUpdate = true;
      if (farCount > 0) runtime.far.buffer.needsUpdate = true;

      // La sombra usa el rango entero sin partir: es un disco de doce vértices,
      // no merece la pena separarlo por distancia.
      if (runtime.all) {
        runtime.all.array.set(source.subarray(start * UNIT_STRIDE, (start + total) * UNIT_STRIDE));
        runtime.all.buffer.needsUpdate = true;
        runtime.all.geometry.instanceCount = total;
      }
    }
  }

  setTeamColors(team: 'red' | 'blue', color: THREE.Color, dark: THREE.Color): void {
    for (const material of this.materialsByTeam[team]) {
      material.uniforms.uTeamColor.value.copy(color);
      material.uniforms.uTeamColorDark.value.copy(dark);
      material.uniforms.uTeamColorLight.value.copy(color).lerp(new THREE.Color('#ffffff'), 0.55);
      material.uniforms.uGlowColor.value.copy(color).lerp(new THREE.Color('#ffffff'), 0.45);
    }
  }

  /** Reloj y viento globales: mueven capas y alas. */
  setTime(time: number, wind: THREE.Vector2): void {
    for (const material of this.materials) {
      material.uniforms.uTime.value = time;
      material.uniforms.uWind.value.copy(wind);
    }
  }

  /** Efectos del clima sobre las unidades: ropa mojada y nieve en los hombros. */
  setWeather(wetness: number, snow: number): void {
    for (const material of this.materials) {
      material.uniforms.uWetness.value = wetness;
      material.uniforms.uSnow.value = snow;
    }
  }

  setTextureDistance(distance: number): void {
    for (const material of this.materials) material.uniforms.uTextureDistance.value = distance;
  }

  setShadowsEnabled(enabled: boolean): void {
    this.shadowMaterial.uniforms.uOpacity.value = enabled ? 0.34 : 0;
  }

  setLodDistance(distance: number): void {
    this.lodDistanceSq = distance * distance;
  }

  setFog(color: THREE.Color, density: number): void {
    for (const material of this.materials) {
      material.uniforms.uFogColor.value.copy(color);
      material.uniforms.uFogDensity.value = density;
    }
  }

  setLighting(sky: THREE.Color, ground: THREE.Color, sun: THREE.Color, dir: THREE.Vector3): void {
    for (const material of this.materials) {
      material.uniforms.uSkyColor.value.copy(sky);
      material.uniforms.uGroundColor.value.copy(ground);
      material.uniforms.uSunColor.value.copy(sun);
      material.uniforms.uLightDir.value.copy(dir).normalize();
    }
    // La sombra se proyecta al lado opuesto de la luz, proporcional a su altura.
    const length = 0.9 / Math.max(0.25, dir.y);
    this.shadowMaterial.uniforms.uLightOffset.value.set(-dir.x * length, -dir.z * length);
  }

  dispose(): void {
    for (const runtime of this.groups) {
      if (!runtime) continue;
      runtime.near.geometry.dispose();
      runtime.far.geometry.dispose();
      runtime.all?.geometry.dispose();
    }
    for (const material of this.materials) material.dispose();
    this.shadowMaterial.dispose();
    for (const geometry of this.baseGeometries) geometry.dispose();
    this.shadowGeometry.dispose();
    this.groups.length = 0;
  }
}
