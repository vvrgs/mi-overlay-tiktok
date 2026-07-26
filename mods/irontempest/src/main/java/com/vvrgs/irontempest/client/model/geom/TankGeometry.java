// GENERADO por tools/gen_models.py — NO editar a mano; edita el spec y regenera.
package com.vvrgs.irontempest.client.model.geom;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class TankGeometry {
    private TankGeometry() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_hull = root.addOrReplaceChild("hull",
                CubeListBuilder.create()
                .texOffs(286, 0).addBox(-13.0000F, -17.0000F, -28.0000F, 26.0000F, 9.0000F, 56.0000F)
                .texOffs(152, 134).addBox(-13.0000F, -19.0000F, 4.0000F, 26.0000F, 2.0000F, 22.0000F)
                .texOffs(100, 181).addBox(-4.0000F, -20.0000F, 14.0000F, 3.0000F, 1.0000F, 8.0000F)
                .texOffs(123, 181).addBox(1.0000F, -20.0000F, 14.0000F, 3.0000F, 1.0000F, 8.0000F)
                .texOffs(214, 74).addBox(-14.0000F, -15.0000F, -24.0000F, 1.0000F, 5.0000F, 48.0000F)
                .texOffs(313, 74).addBox(13.0000F, -15.0000F, -24.0000F, 1.0000F, 5.0000F, 48.0000F)
                .texOffs(146, 181).addBox(-10.0000F, -19.4000F, 6.0000F, 8.0000F, 1.0000F, 8.0000F)
                .texOffs(179, 181).addBox(2.0000F, -19.4000F, 6.0000F, 8.0000F, 1.0000F, 8.0000F)
                .texOffs(360, 181).addBox(-9.0000F, -19.4000F, 16.0000F, 18.0000F, 1.0000F, 6.0000F)
                .texOffs(409, 181).addBox(-3.0000F, -17.6000F, -24.0000F, 6.0000F, 1.0000F, 6.0000F)
                .texOffs(394, 192).addBox(-10.0000F, -14.0000F, -29.5000F, 2.0000F, 3.0000F, 2.0000F)
                .texOffs(403, 192).addBox(8.0000F, -14.0000F, -29.5000F, 2.0000F, 3.0000F, 2.0000F)
                .texOffs(412, 192).addBox(-25.0000F, -15.5000F, -33.0000F, 11.0000F, 1.0000F, 4.0000F)
                .texOffs(443, 192).addBox(14.0000F, -15.5000F, -33.0000F, 11.0000F, 1.0000F, 4.0000F)
                .texOffs(474, 192).addBox(-25.0000F, -15.5000F, 29.0000F, 11.0000F, 1.0000F, 4.0000F)
                .texOffs(0, 200).addBox(14.0000F, -15.5000F, 29.0000F, 11.0000F, 1.0000F, 4.0000F)
                .texOffs(249, 134).addBox(13.2000F, -13.0000F, -6.0000F, 2.0000F, 4.0000F, 14.0000F)
                .texOffs(412, 74).addBox(-13.8000F, -15.0000F, -20.0000F, 1.0000F, 1.0000F, 34.0000F),
                PartPose.offset(0.0000F, 24.0000F, 0.0000F));
        PartDefinition p_cans = root.addOrReplaceChild("cans",
                CubeListBuilder.create()
                .texOffs(178, 168).addBox(-1.5000F, -3.0000F, -3.0000F, 3.0000F, 6.0000F, 4.0000F)
                .texOffs(193, 168).addBox(-1.5000F, -3.0000F, 2.0000F, 3.0000F, 6.0000F, 4.0000F),
                PartPose.offsetAndRotation(-14.0000F, 8.0000F, 18.0000F, 0.0000F, 0.0698F, 0.0000F));
        PartDefinition p_glacis = p_hull.addOrReplaceChild("glacis",
                CubeListBuilder.create()
                .texOffs(282, 134).addBox(-13.0000F, -1.0000F, -14.0000F, 26.0000F, 2.0000F, 14.0000F)
                .texOffs(434, 181).addBox(-12.0000F, -3.0000F, -13.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(453, 181).addBox(-7.0000F, -3.0000F, -13.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(472, 181).addBox(-2.0000F, -3.0000F, -13.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(491, 181).addBox(3.0000F, -3.0000F, -13.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(0, 192).addBox(8.0000F, -3.0000F, -13.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(19, 192).addBox(-12.0000F, -3.0000F, -7.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(38, 192).addBox(-7.0000F, -3.0000F, -7.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(57, 192).addBox(-2.0000F, -3.0000F, -7.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(76, 192).addBox(3.0000F, -3.0000F, -7.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(95, 192).addBox(8.0000F, -3.0000F, -7.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(31, 200).addBox(-10.0000F, -4.2000F, -1.5000F, 6.0000F, 1.0000F, 4.0000F)
                .texOffs(52, 200).addBox(4.0000F, -4.2000F, -1.5000F, 6.0000F, 1.0000F, 4.0000F),
                PartPose.offsetAndRotation(0.0000F, -16.0000F, -28.0000F, 0.6632F, 0.0000F, 0.0000F));
        PartDefinition p_rear_plate = root.addOrReplaceChild("rear_plate",
                CubeListBuilder.create()
                .texOffs(0, 168).addBox(-13.0000F, -1.0000F, 0.0000F, 26.0000F, 2.0000F, 10.0000F),
                PartPose.offsetAndRotation(0.0000F, 7.0000F, 28.0000F, -0.5236F, 0.0000F, 0.0000F));
        PartDefinition p_plow = root.addOrReplaceChild("plow",
                CubeListBuilder.create()
                .texOffs(208, 168).addBox(-11.0000F, -6.0000F, -2.0000F, 22.0000F, 8.0000F, 2.0000F)
                .texOffs(196, 200).addBox(-10.0000F, 1.5000F, -3.0000F, 2.0000F, 1.0000F, 2.0000F)
                .texOffs(205, 200).addBox(-5.5000F, 1.5000F, -3.0000F, 2.0000F, 1.0000F, 2.0000F)
                .texOffs(214, 200).addBox(-1.0000F, 1.5000F, -3.0000F, 2.0000F, 1.0000F, 2.0000F)
                .texOffs(223, 200).addBox(3.5000F, 1.5000F, -3.0000F, 2.0000F, 1.0000F, 2.0000F)
                .texOffs(232, 200).addBox(8.0000F, 1.5000F, -3.0000F, 2.0000F, 1.0000F, 2.0000F),
                PartPose.offsetAndRotation(0.0000F, 18.0000F, -30.0000F, -0.2618F, 0.0000F, 0.0000F));
        PartDefinition p_headlight_l = root.addOrReplaceChild("headlight_l",
                CubeListBuilder.create()
                .texOffs(241, 200).addBox(-1.5000F, -1.0000F, -1.0000F, 3.0000F, 2.0000F, 1.0000F),
                PartPose.offset(9.0000F, 8.5000F, -29.0000F));
        PartDefinition p_headlight_r = root.addOrReplaceChild("headlight_r",
                CubeListBuilder.create()
                .texOffs(250, 200).addBox(-1.5000F, -1.0000F, -1.0000F, 3.0000F, 2.0000F, 1.0000F),
                PartPose.offset(-9.0000F, 8.5000F, -29.0000F));
        PartDefinition p_drum_l = root.addOrReplaceChild("drum_l",
                CubeListBuilder.create()
                .texOffs(372, 134).addBox(-3.0000F, -3.0000F, 0.0000F, 6.0000F, 6.0000F, 8.0000F),
                PartPose.offset(-8.0000F, 6.0000F, 30.0000F));
        PartDefinition p_drum_l_x = root.addOrReplaceChild("drum_l_x",
                CubeListBuilder.create()
                .texOffs(401, 134).addBox(-3.0000F, -3.0000F, 0.0000F, 6.0000F, 6.0000F, 8.0000F),
                PartPose.offsetAndRotation(-8.0000F, 6.0000F, 30.0000F, 0.0000F, 0.0000F, 0.7854F));
        PartDefinition p_drum_r = root.addOrReplaceChild("drum_r",
                CubeListBuilder.create()
                .texOffs(430, 134).addBox(-3.0000F, -3.0000F, 0.0000F, 6.0000F, 6.0000F, 8.0000F),
                PartPose.offset(8.0000F, 6.0000F, 30.0000F));
        PartDefinition p_drum_r_x = root.addOrReplaceChild("drum_r_x",
                CubeListBuilder.create()
                .texOffs(459, 134).addBox(-3.0000F, -3.0000F, 0.0000F, 6.0000F, 6.0000F, 8.0000F),
                PartPose.offsetAndRotation(8.0000F, 6.0000F, 30.0000F, 0.0000F, 0.0000F, 0.7854F));
        PartDefinition p_track_l = root.addOrReplaceChild("track_l",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(14.0000F, -13.0000F, -30.0000F, 11.0000F, 13.0000F, 60.0000F)
                .texOffs(0, 74).addBox(25.0000F, -15.0000F, -26.0000F, 1.0000F, 7.0000F, 52.0000F)
                .texOffs(73, 168).addBox(15.0000F, -11.0000F, -33.0000F, 9.0000F, 9.0000F, 3.0000F)
                .texOffs(98, 168).addBox(15.0000F, -10.0000F, 28.0000F, 9.0000F, 9.0000F, 3.0000F)
                .texOffs(193, 192).addBox(17.0000F, -9.0000F, -16.0000F, 5.0000F, 3.0000F, 3.0000F)
                .texOffs(210, 192).addBox(17.0000F, -9.0000F, -1.0000F, 5.0000F, 3.0000F, 3.0000F)
                .texOffs(227, 192).addBox(17.0000F, -9.0000F, 13.0000F, 5.0000F, 3.0000F, 3.0000F)
                .texOffs(122, 200).addBox(25.4000F, -16.0000F, -20.0000F, 1.0000F, 2.0000F, 2.0000F)
                .texOffs(129, 200).addBox(25.4000F, -16.0000F, -8.0000F, 1.0000F, 2.0000F, 2.0000F)
                .texOffs(136, 200).addBox(25.4000F, -16.0000F, 4.0000F, 1.0000F, 2.0000F, 2.0000F)
                .texOffs(143, 200).addBox(25.4000F, -16.0000F, 16.0000F, 1.0000F, 2.0000F, 2.0000F),
                PartPose.offset(0.0000F, 24.0000F, 0.0000F));
        PartDefinition p_wheel_l0 = p_track_l.addOrReplaceChild("wheel_l0",
                CubeListBuilder.create()
                .texOffs(257, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, -22.0000F));
        PartDefinition p_wheel_l1 = p_track_l.addOrReplaceChild("wheel_l1",
                CubeListBuilder.create()
                .texOffs(278, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, -11.0000F));
        PartDefinition p_wheel_l2 = p_track_l.addOrReplaceChild("wheel_l2",
                CubeListBuilder.create()
                .texOffs(299, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, 0.0000F));
        PartDefinition p_wheel_l3 = p_track_l.addOrReplaceChild("wheel_l3",
                CubeListBuilder.create()
                .texOffs(320, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, 11.0000F));
        PartDefinition p_wheel_l4 = p_track_l.addOrReplaceChild("wheel_l4",
                CubeListBuilder.create()
                .texOffs(341, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, 22.0000F));
        PartDefinition p_track_r = root.addOrReplaceChild("track_r",
                CubeListBuilder.create()
                .texOffs(143, 0).addBox(-25.0000F, -13.0000F, -30.0000F, 11.0000F, 13.0000F, 60.0000F)
                .texOffs(107, 74).addBox(-26.0000F, -15.0000F, -26.0000F, 1.0000F, 7.0000F, 52.0000F)
                .texOffs(123, 168).addBox(-24.0000F, -11.0000F, -33.0000F, 9.0000F, 9.0000F, 3.0000F)
                .texOffs(148, 168).addBox(-24.0000F, -10.0000F, 28.0000F, 9.0000F, 9.0000F, 3.0000F)
                .texOffs(244, 192).addBox(-22.0000F, -9.0000F, -16.0000F, 5.0000F, 3.0000F, 3.0000F)
                .texOffs(261, 192).addBox(-22.0000F, -9.0000F, -1.0000F, 5.0000F, 3.0000F, 3.0000F)
                .texOffs(278, 192).addBox(-22.0000F, -9.0000F, 13.0000F, 5.0000F, 3.0000F, 3.0000F)
                .texOffs(150, 200).addBox(-26.4000F, -16.0000F, -20.0000F, 1.0000F, 2.0000F, 2.0000F)
                .texOffs(157, 200).addBox(-26.4000F, -16.0000F, -8.0000F, 1.0000F, 2.0000F, 2.0000F)
                .texOffs(164, 200).addBox(-26.4000F, -16.0000F, 4.0000F, 1.0000F, 2.0000F, 2.0000F)
                .texOffs(171, 200).addBox(-26.4000F, -16.0000F, 16.0000F, 1.0000F, 2.0000F, 2.0000F),
                PartPose.offset(0.0000F, 24.0000F, 0.0000F));
        PartDefinition p_wheel_r0 = p_track_r.addOrReplaceChild("wheel_r0",
                CubeListBuilder.create()
                .texOffs(362, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, -22.0000F));
        PartDefinition p_wheel_r1 = p_track_r.addOrReplaceChild("wheel_r1",
                CubeListBuilder.create()
                .texOffs(383, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, -11.0000F));
        PartDefinition p_wheel_r2 = p_track_r.addOrReplaceChild("wheel_r2",
                CubeListBuilder.create()
                .texOffs(404, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, 0.0000F));
        PartDefinition p_wheel_r3 = p_track_r.addOrReplaceChild("wheel_r3",
                CubeListBuilder.create()
                .texOffs(425, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, 11.0000F));
        PartDefinition p_wheel_r4 = p_track_r.addOrReplaceChild("wheel_r4",
                CubeListBuilder.create()
                .texOffs(446, 168).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, 22.0000F));
        PartDefinition p_turret = root.addOrReplaceChild("turret",
                CubeListBuilder.create()
                .texOffs(0, 134).addBox(-11.0000F, -7.0000F, -13.0000F, 22.0000F, 7.0000F, 26.0000F)
                .texOffs(279, 181).addBox(-9.0000F, -2.0000F, 13.0000F, 18.0000F, 4.0000F, 4.0000F)
                .texOffs(114, 192).addBox(-9.0000F, -6.0000F, -15.0000F, 18.0000F, 5.0000F, 2.0000F)
                .texOffs(467, 168).addBox(-12.0000F, -6.0000F, -10.0000F, 1.0000F, 4.0000F, 6.0000F)
                .texOffs(482, 168).addBox(-12.0000F, -6.0000F, -3.0000F, 1.0000F, 4.0000F, 6.0000F)
                .texOffs(497, 168).addBox(-12.0000F, -6.0000F, 4.0000F, 1.0000F, 4.0000F, 6.0000F)
                .texOffs(0, 181).addBox(11.0000F, -6.0000F, -10.0000F, 1.0000F, 4.0000F, 6.0000F)
                .texOffs(15, 181).addBox(11.0000F, -6.0000F, -3.0000F, 1.0000F, 4.0000F, 6.0000F)
                .texOffs(30, 181).addBox(11.0000F, -6.0000F, 4.0000F, 1.0000F, 4.0000F, 6.0000F)
                .texOffs(155, 192).addBox(-7.0000F, -9.0000F, -11.0000F, 4.0000F, 2.0000F, 5.0000F)
                .texOffs(174, 192).addBox(2.0000F, -10.0000F, -12.0000F, 5.0000F, 3.0000F, 4.0000F)
                .texOffs(73, 200).addBox(0.0000F, -13.0000F, 10.0000F, 1.0000F, 4.0000F, 1.0000F)
                .texOffs(178, 200).addBox(-10.0000F, -8.0000F, -2.0000F, 1.0000F, 1.0000F, 3.0000F)
                .texOffs(187, 200).addBox(9.0000F, -8.0000F, -2.0000F, 1.0000F, 1.0000F, 3.0000F)
                .texOffs(45, 181).addBox(-11.0000F, -5.0000F, 9.0000F, 2.0000F, 4.0000F, 6.0000F)
                .texOffs(62, 181).addBox(9.0000F, -5.0000F, 9.0000F, 2.0000F, 4.0000F, 6.0000F)
                .texOffs(212, 181).addBox(-4.0000F, -6.0000F, 17.2000F, 8.0000F, 8.0000F, 1.0000F),
                PartPose.offset(0.0000F, 7.0000F, -2.0000F));
        PartDefinition p_snorkel = p_turret.addOrReplaceChild("snorkel",
                CubeListBuilder.create()
                .texOffs(363, 134).addBox(-1.0000F, -14.0000F, -1.0000F, 2.0000F, 14.0000F, 2.0000F),
                PartPose.offsetAndRotation(9.0000F, -4.0000F, 12.0000F, -0.2094F, 0.0000F, -0.1047F));
        PartDefinition p_barrel = p_turret.addOrReplaceChild("barrel",
                CubeListBuilder.create()
                .texOffs(324, 181).addBox(-3.0000F, -2.5000F, -3.0000F, 6.0000F, 5.0000F, 3.0000F)
                .texOffs(97, 134).addBox(-1.5000F, -1.5000F, -27.0000F, 3.0000F, 3.0000F, 24.0000F)
                .texOffs(343, 181).addBox(-2.0000F, -2.0000F, -31.0000F, 4.0000F, 4.0000F, 4.0000F)
                .texOffs(79, 181).addBox(-2.0000F, -2.0000F, -17.0000F, 4.0000F, 4.0000F, 6.0000F)
                .texOffs(78, 200).addBox(-2.0000F, -2.0000F, -10.0000F, 4.0000F, 4.0000F, 1.0000F)
                .texOffs(89, 200).addBox(-2.0000F, -2.0000F, -23.0000F, 4.0000F, 4.0000F, 1.0000F)
                .texOffs(259, 200).addBox(1.5000F, -2.8000F, -29.0000F, 1.0000F, 1.0000F, 2.0000F),
                PartPose.offset(0.0000F, -10.5000F, -11.0000F));
        PartDefinition p_cupola = p_turret.addOrReplaceChild("cupola",
                CubeListBuilder.create()
                .texOffs(231, 181).addBox(-3.5000F, -2.0000F, -3.5000F, 7.0000F, 2.0000F, 7.0000F)
                .texOffs(295, 192).addBox(-2.5000F, -3.0000F, -2.5000F, 5.0000F, 1.0000F, 5.0000F)
                .texOffs(280, 200).addBox(-1.0000F, -1.6000F, -4.2000F, 2.0000F, 1.0000F, 1.0000F)
                .texOffs(287, 200).addBox(-1.0000F, -1.6000F, 3.2000F, 2.0000F, 1.0000F, 1.0000F)
                .texOffs(266, 200).addBox(-4.2000F, -1.6000F, -1.0000F, 1.0000F, 1.0000F, 2.0000F)
                .texOffs(273, 200).addBox(3.2000F, -1.6000F, -1.0000F, 1.0000F, 1.0000F, 2.0000F),
                PartPose.offset(5.0000F, -14.0000F, 8.0000F));
        PartDefinition p_mg = p_turret.addOrReplaceChild("mg",
                CubeListBuilder.create()
                .texOffs(260, 181).addBox(-0.5000F, -0.5000F, -7.0000F, 1.0000F, 1.0000F, 8.0000F)
                .texOffs(100, 200).addBox(-1.0000F, -1.5000F, -3.0000F, 2.0000F, 2.0000F, 3.0000F)
                .texOffs(111, 200).addBox(1.0000F, -1.5000F, -1.0000F, 2.0000F, 2.0000F, 3.0000F),
                PartPose.offset(5.0000F, -16.0000F, 6.0000F));
        PartDefinition p_ant0 = p_turret.addOrReplaceChild("ant0",
                CubeListBuilder.create()
                .texOffs(488, 134).addBox(-0.5000F, -12.0000F, -0.5000F, 1.0000F, 12.0000F, 1.0000F),
                PartPose.offsetAndRotation(-8.0000F, -14.0000F, 13.0000F, 0.0000F, 0.0000F, 0.1047F));
        PartDefinition p_ant1 = p_turret.addOrReplaceChild("ant1",
                CubeListBuilder.create()
                .texOffs(173, 168).addBox(-0.5000F, -10.0000F, -0.5000F, 1.0000F, 10.0000F, 1.0000F),
                PartPose.offsetAndRotation(8.0000F, -14.0000F, 14.0000F, -0.1396F, 0.0000F, 0.0000F));
        PartDefinition p_smoke_l = p_turret.addOrReplaceChild("smoke_l",
                CubeListBuilder.create()
                .texOffs(316, 192).addBox(0.0000F, -1.0000F, -4.0000F, 2.0000F, 2.0000F, 4.0000F)
                .texOffs(329, 192).addBox(0.0000F, -1.0000F, -0.5000F, 2.0000F, 2.0000F, 4.0000F)
                .texOffs(342, 192).addBox(0.0000F, -1.0000F, 3.0000F, 2.0000F, 2.0000F, 4.0000F),
                PartPose.offsetAndRotation(11.0000F, -11.0000F, -4.0000F, -0.1745F, -0.4363F, 0.0000F));
        PartDefinition p_smoke_r = p_turret.addOrReplaceChild("smoke_r",
                CubeListBuilder.create()
                .texOffs(355, 192).addBox(0.0000F, -1.0000F, -4.0000F, 2.0000F, 2.0000F, 4.0000F)
                .texOffs(368, 192).addBox(0.0000F, -1.0000F, -0.5000F, 2.0000F, 2.0000F, 4.0000F)
                .texOffs(381, 192).addBox(0.0000F, -1.0000F, 3.0000F, 2.0000F, 2.0000F, 4.0000F),
                PartPose.offsetAndRotation(-13.0000F, -11.0000F, -4.0000F, -0.1745F, 0.4363F, 0.0000F));
        return LayerDefinition.create(mesh, 512, 512);
    }
}
