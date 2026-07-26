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
                .texOffs(366, 74).addBox(-13.0000F, -19.0000F, 4.0000F, 26.0000F, 2.0000F, 22.0000F)
                .texOffs(84, 151).addBox(-4.0000F, -20.0000F, 14.0000F, 3.0000F, 1.0000F, 8.0000F)
                .texOffs(107, 151).addBox(1.0000F, -20.0000F, 14.0000F, 3.0000F, 1.0000F, 8.0000F),
                PartPose.offset(0.0000F, 24.0000F, 0.0000F));
        PartDefinition p_glacis = p_hull.addOrReplaceChild("glacis",
                CubeListBuilder.create()
                .texOffs(0, 134).addBox(-13.0000F, -1.0000F, -14.0000F, 26.0000F, 2.0000F, 14.0000F),
                PartPose.offsetAndRotation(0.0000F, -16.0000F, -28.0000F, 0.6632F, 0.0000F, 0.0000F));
        PartDefinition p_rear_plate = root.addOrReplaceChild("rear_plate",
                CubeListBuilder.create()
                .texOffs(202, 134).addBox(-13.0000F, -1.0000F, 0.0000F, 26.0000F, 2.0000F, 10.0000F),
                PartPose.offsetAndRotation(0.0000F, 7.0000F, 28.0000F, -0.5236F, 0.0000F, 0.0000F));
        PartDefinition p_plow = root.addOrReplaceChild("plow",
                CubeListBuilder.create()
                .texOffs(330, 134).addBox(-11.0000F, -6.0000F, -2.0000F, 22.0000F, 8.0000F, 2.0000F),
                PartPose.offsetAndRotation(0.0000F, 18.0000F, -30.0000F, -0.2618F, 0.0000F, 0.0000F));
        PartDefinition p_headlight_l = root.addOrReplaceChild("headlight_l",
                CubeListBuilder.create()
                .texOffs(369, 151).addBox(-1.5000F, -1.0000F, -1.0000F, 3.0000F, 2.0000F, 1.0000F),
                PartPose.offset(9.0000F, 8.5000F, -29.0000F));
        PartDefinition p_headlight_r = root.addOrReplaceChild("headlight_r",
                CubeListBuilder.create()
                .texOffs(378, 151).addBox(-1.5000F, -1.0000F, -1.0000F, 3.0000F, 2.0000F, 1.0000F),
                PartPose.offset(-9.0000F, 8.5000F, -29.0000F));
        PartDefinition p_drum_l = root.addOrReplaceChild("drum_l",
                CubeListBuilder.create()
                .texOffs(81, 134).addBox(-3.0000F, -3.0000F, 0.0000F, 6.0000F, 6.0000F, 8.0000F),
                PartPose.offset(-8.0000F, 6.0000F, 30.0000F));
        PartDefinition p_drum_l_x = root.addOrReplaceChild("drum_l_x",
                CubeListBuilder.create()
                .texOffs(110, 134).addBox(-3.0000F, -3.0000F, 0.0000F, 6.0000F, 6.0000F, 8.0000F),
                PartPose.offsetAndRotation(-8.0000F, 6.0000F, 30.0000F, 0.0000F, 0.0000F, 0.7854F));
        PartDefinition p_drum_r = root.addOrReplaceChild("drum_r",
                CubeListBuilder.create()
                .texOffs(139, 134).addBox(-3.0000F, -3.0000F, 0.0000F, 6.0000F, 6.0000F, 8.0000F),
                PartPose.offset(8.0000F, 6.0000F, 30.0000F));
        PartDefinition p_drum_r_x = root.addOrReplaceChild("drum_r_x",
                CubeListBuilder.create()
                .texOffs(168, 134).addBox(-3.0000F, -3.0000F, 0.0000F, 6.0000F, 6.0000F, 8.0000F),
                PartPose.offsetAndRotation(8.0000F, 6.0000F, 30.0000F, 0.0000F, 0.0000F, 0.7854F));
        PartDefinition p_track_l = root.addOrReplaceChild("track_l",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(14.0000F, -13.0000F, -30.0000F, 11.0000F, 13.0000F, 60.0000F)
                .texOffs(0, 74).addBox(25.0000F, -15.0000F, -26.0000F, 1.0000F, 7.0000F, 52.0000F)
                .texOffs(275, 134).addBox(15.0000F, -11.0000F, -33.0000F, 9.0000F, 9.0000F, 3.0000F),
                PartPose.offset(0.0000F, 24.0000F, 0.0000F));
        PartDefinition p_wheel_l0 = p_track_l.addOrReplaceChild("wheel_l0",
                CubeListBuilder.create()
                .texOffs(379, 134).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, -22.0000F));
        PartDefinition p_wheel_l1 = p_track_l.addOrReplaceChild("wheel_l1",
                CubeListBuilder.create()
                .texOffs(400, 134).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, -11.0000F));
        PartDefinition p_wheel_l2 = p_track_l.addOrReplaceChild("wheel_l2",
                CubeListBuilder.create()
                .texOffs(421, 134).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, 0.0000F));
        PartDefinition p_wheel_l3 = p_track_l.addOrReplaceChild("wheel_l3",
                CubeListBuilder.create()
                .texOffs(442, 134).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, 11.0000F));
        PartDefinition p_wheel_l4 = p_track_l.addOrReplaceChild("wheel_l4",
                CubeListBuilder.create()
                .texOffs(463, 134).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(19.5000F, -6.0000F, 22.0000F));
        PartDefinition p_track_r = root.addOrReplaceChild("track_r",
                CubeListBuilder.create()
                .texOffs(143, 0).addBox(-25.0000F, -13.0000F, -30.0000F, 11.0000F, 13.0000F, 60.0000F)
                .texOffs(107, 74).addBox(-26.0000F, -15.0000F, -26.0000F, 1.0000F, 7.0000F, 52.0000F)
                .texOffs(300, 134).addBox(-24.0000F, -11.0000F, -33.0000F, 9.0000F, 9.0000F, 3.0000F),
                PartPose.offset(0.0000F, 24.0000F, 0.0000F));
        PartDefinition p_wheel_r0 = p_track_r.addOrReplaceChild("wheel_r0",
                CubeListBuilder.create()
                .texOffs(484, 134).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, -22.0000F));
        PartDefinition p_wheel_r1 = p_track_r.addOrReplaceChild("wheel_r1",
                CubeListBuilder.create()
                .texOffs(0, 151).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, -11.0000F));
        PartDefinition p_wheel_r2 = p_track_r.addOrReplaceChild("wheel_r2",
                CubeListBuilder.create()
                .texOffs(21, 151).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, 0.0000F));
        PartDefinition p_wheel_r3 = p_track_r.addOrReplaceChild("wheel_r3",
                CubeListBuilder.create()
                .texOffs(42, 151).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, 11.0000F));
        PartDefinition p_wheel_r4 = p_track_r.addOrReplaceChild("wheel_r4",
                CubeListBuilder.create()
                .texOffs(63, 151).addBox(-3.5000F, -3.5000F, -1.5000F, 7.0000F, 7.0000F, 3.0000F),
                PartPose.offset(-19.5000F, -6.0000F, 22.0000F));
        PartDefinition p_turret = root.addOrReplaceChild("turret",
                CubeListBuilder.create()
                .texOffs(214, 74).addBox(-11.0000F, -7.0000F, -13.0000F, 22.0000F, 7.0000F, 26.0000F)
                .texOffs(178, 151).addBox(-9.0000F, -2.0000F, 13.0000F, 18.0000F, 4.0000F, 4.0000F),
                PartPose.offset(0.0000F, 7.0000F, -2.0000F));
        PartDefinition p_barrel = p_turret.addOrReplaceChild("barrel",
                CubeListBuilder.create()
                .texOffs(223, 151).addBox(-3.0000F, -2.5000F, -3.0000F, 6.0000F, 5.0000F, 3.0000F)
                .texOffs(311, 74).addBox(-1.5000F, -1.5000F, -27.0000F, 3.0000F, 3.0000F, 24.0000F)
                .texOffs(242, 151).addBox(-2.0000F, -2.0000F, -31.0000F, 4.0000F, 4.0000F, 4.0000F),
                PartPose.offset(0.0000F, -10.5000F, -11.0000F));
        PartDefinition p_cupola = p_turret.addOrReplaceChild("cupola",
                CubeListBuilder.create()
                .texOffs(130, 151).addBox(-3.5000F, -2.0000F, -3.5000F, 7.0000F, 2.0000F, 7.0000F)
                .texOffs(259, 151).addBox(-2.5000F, -3.0000F, -2.5000F, 5.0000F, 1.0000F, 5.0000F),
                PartPose.offset(5.0000F, -14.0000F, 8.0000F));
        PartDefinition p_mg = p_turret.addOrReplaceChild("mg",
                CubeListBuilder.create()
                .texOffs(159, 151).addBox(-0.5000F, -0.5000F, -7.0000F, 1.0000F, 1.0000F, 8.0000F)
                .texOffs(358, 151).addBox(-1.0000F, -1.5000F, -3.0000F, 2.0000F, 2.0000F, 3.0000F),
                PartPose.offset(5.0000F, -16.0000F, 6.0000F));
        PartDefinition p_ant0 = p_turret.addOrReplaceChild("ant0",
                CubeListBuilder.create()
                .texOffs(197, 134).addBox(-0.5000F, -12.0000F, -0.5000F, 1.0000F, 12.0000F, 1.0000F),
                PartPose.offsetAndRotation(-8.0000F, -14.0000F, 13.0000F, 0.0000F, 0.0000F, 0.1047F));
        PartDefinition p_ant1 = p_turret.addOrReplaceChild("ant1",
                CubeListBuilder.create()
                .texOffs(325, 134).addBox(-0.5000F, -10.0000F, -0.5000F, 1.0000F, 10.0000F, 1.0000F),
                PartPose.offsetAndRotation(8.0000F, -14.0000F, 14.0000F, -0.1396F, 0.0000F, 0.0000F));
        PartDefinition p_smoke_l = p_turret.addOrReplaceChild("smoke_l",
                CubeListBuilder.create()
                .texOffs(280, 151).addBox(0.0000F, -1.0000F, -4.0000F, 2.0000F, 2.0000F, 4.0000F)
                .texOffs(293, 151).addBox(0.0000F, -1.0000F, -0.5000F, 2.0000F, 2.0000F, 4.0000F)
                .texOffs(306, 151).addBox(0.0000F, -1.0000F, 3.0000F, 2.0000F, 2.0000F, 4.0000F),
                PartPose.offsetAndRotation(11.0000F, -11.0000F, -4.0000F, -0.1745F, -0.4363F, 0.0000F));
        PartDefinition p_smoke_r = p_turret.addOrReplaceChild("smoke_r",
                CubeListBuilder.create()
                .texOffs(319, 151).addBox(0.0000F, -1.0000F, -4.0000F, 2.0000F, 2.0000F, 4.0000F)
                .texOffs(332, 151).addBox(0.0000F, -1.0000F, -0.5000F, 2.0000F, 2.0000F, 4.0000F)
                .texOffs(345, 151).addBox(0.0000F, -1.0000F, 3.0000F, 2.0000F, 2.0000F, 4.0000F),
                PartPose.offsetAndRotation(-13.0000F, -11.0000F, -4.0000F, -0.1745F, 0.4363F, 0.0000F));
        return LayerDefinition.create(mesh, 512, 512);
    }
}
