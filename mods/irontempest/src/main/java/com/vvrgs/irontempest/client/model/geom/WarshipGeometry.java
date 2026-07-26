// GENERADO por tools/gen_models.py — NO editar a mano; edita el spec y regenera.
package com.vvrgs.irontempest.client.model.geom;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class WarshipGeometry {
    private WarshipGeometry() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_hull = root.addOrReplaceChild("hull",
                CubeListBuilder.create()
                .texOffs(173, 0).addBox(-14.0000F, -6.0000F, -30.0000F, 28.0000F, 14.0000F, 60.0000F)
                .texOffs(0, 79).addBox(-12.0000F, -4.0000F, 30.0000F, 24.0000F, 12.0000F, 28.0000F)
                .texOffs(237, 120).addBox(-8.0000F, -2.0000F, 58.0000F, 16.0000F, 8.0000F, 10.0000F)
                .texOffs(0, 0).addBox(-6.0000F, -10.0000F, -34.0000F, 12.0000F, 4.0000F, 74.0000F)
                .texOffs(350, 0).addBox(-4.0000F, 8.0000F, -20.0000F, 8.0000F, 3.0000F, 44.0000F)
                .texOffs(33, 146).addBox(-3.0000F, -13.0000F, -16.0000F, 6.0000F, 3.0000F, 6.0000F),
                PartPose.offset(0.0000F, 0.0000F, 0.0000F));
        PartDefinition p_prow = root.addOrReplaceChild("prow",
                CubeListBuilder.create()
                .texOffs(0, 120).addBox(-11.0000F, -5.0000F, -14.0000F, 22.0000F, 11.0000F, 14.0000F)
                .texOffs(184, 120).addBox(-7.0000F, -3.0000F, -26.0000F, 14.0000F, 7.0000F, 12.0000F)
                .texOffs(369, 120).addBox(-3.0000F, -1.0000F, -34.0000F, 6.0000F, 4.0000F, 8.0000F),
                PartPose.offset(0.0000F, 0.0000F, -30.0000F));
        PartDefinition p_bridge = root.addOrReplaceChild("bridge",
                CubeListBuilder.create()
                .texOffs(73, 120).addBox(-7.0000F, -8.0000F, -6.0000F, 14.0000F, 8.0000F, 12.0000F)
                .texOffs(398, 120).addBox(-5.0000F, -12.0000F, -4.0000F, 10.0000F, 4.0000F, 8.0000F)
                .texOffs(58, 146).addBox(-3.0000F, -20.0000F, 2.0000F, 1.0000F, 8.0000F, 1.0000F)
                .texOffs(97, 146).addBox(2.0000F, -18.0000F, 3.0000F, 1.0000F, 6.0000F, 1.0000F),
                PartPose.offset(0.0000F, -10.0000F, 18.0000F));
        PartDefinition p_wing_l = root.addOrReplaceChild("wing_l",
                CubeListBuilder.create()
                .texOffs(243, 79).addBox(0.0000F, 0.0000F, -8.0000F, 30.0000F, 2.0000F, 24.0000F)
                .texOffs(126, 120).addBox(28.0000F, -6.0000F, 4.0000F, 2.0000F, 8.0000F, 12.0000F),
                PartPose.offsetAndRotation(14.0000F, -2.0000F, 6.0000F, 0.0000F, 0.3840F, 0.1396F));
        PartDefinition p_wing_r = root.addOrReplaceChild("wing_r",
                CubeListBuilder.create()
                .texOffs(352, 79).addBox(-30.0000F, 0.0000F, -8.0000F, 30.0000F, 2.0000F, 24.0000F)
                .texOffs(155, 120).addBox(-30.0000F, -6.0000F, 4.0000F, 2.0000F, 8.0000F, 12.0000F),
                PartPose.offsetAndRotation(-14.0000F, -2.0000F, 6.0000F, 0.0000F, -0.3840F, -0.1396F));
        PartDefinition p_nacelle_l = root.addOrReplaceChild("nacelle_l",
                CubeListBuilder.create()
                .texOffs(105, 79).addBox(-4.0000F, -4.0000F, -10.0000F, 8.0000F, 8.0000F, 26.0000F)
                .texOffs(435, 120).addBox(-5.0000F, -5.0000F, -12.0000F, 10.0000F, 10.0000F, 2.0000F)
                .texOffs(63, 146).addBox(-3.0000F, -3.0000F, 16.0000F, 6.0000F, 6.0000F, 2.0000F),
                PartPose.offset(16.0000F, 2.0000F, 26.0000F));
        PartDefinition p_nacelle_r = root.addOrReplaceChild("nacelle_r",
                CubeListBuilder.create()
                .texOffs(174, 79).addBox(-4.0000F, -4.0000F, -10.0000F, 8.0000F, 8.0000F, 26.0000F)
                .texOffs(460, 120).addBox(-5.0000F, -5.0000F, -12.0000F, 10.0000F, 10.0000F, 2.0000F)
                .texOffs(80, 146).addBox(-3.0000F, -3.0000F, 16.0000F, 6.0000F, 6.0000F, 2.0000F),
                PartPose.offset(-16.0000F, 2.0000F, 26.0000F));
        PartDefinition p_pod_l = root.addOrReplaceChild("pod_l",
                CubeListBuilder.create()
                .texOffs(290, 120).addBox(0.0000F, 0.0000F, -6.0000F, 3.0000F, 4.0000F, 12.0000F),
                PartPose.offset(15.0000F, -8.0000F, -8.0000F));
        PartDefinition p_pod_r = root.addOrReplaceChild("pod_r",
                CubeListBuilder.create()
                .texOffs(321, 120).addBox(0.0000F, 0.0000F, -6.0000F, 3.0000F, 4.0000F, 12.0000F),
                PartPose.offset(-18.0000F, -8.0000F, -8.0000F));
        PartDefinition p_cannon = root.addOrReplaceChild("cannon",
                CubeListBuilder.create()
                .texOffs(0, 146).addBox(-4.0000F, -1.0000F, -4.0000F, 8.0000F, 3.0000F, 8.0000F),
                PartPose.offset(0.0000F, 8.0000F, -6.0000F));
        PartDefinition p_cannon_barrel = p_cannon.addOrReplaceChild("cannon_barrel",
                CubeListBuilder.create()
                .texOffs(352, 120).addBox(-2.0000F, 0.0000F, -2.0000F, 4.0000F, 12.0000F, 4.0000F)
                .texOffs(102, 146).addBox(-2.5000F, 12.0000F, -2.5000F, 5.0000F, 2.0000F, 5.0000F),
                PartPose.offset(0.0000F, -6.0000F, 6.0000F));
        return LayerDefinition.create(mesh, 512, 512);
    }
}
