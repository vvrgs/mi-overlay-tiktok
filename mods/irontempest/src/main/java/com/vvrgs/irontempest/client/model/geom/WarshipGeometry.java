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
                .texOffs(190, 79).addBox(-12.0000F, -4.0000F, 30.0000F, 24.0000F, 12.0000F, 28.0000F)
                .texOffs(455, 127).addBox(-8.0000F, -2.0000F, 58.0000F, 16.0000F, 8.0000F, 10.0000F)
                .texOffs(0, 0).addBox(-6.0000F, -10.0000F, -34.0000F, 12.0000F, 4.0000F, 74.0000F)
                .texOffs(350, 0).addBox(-4.0000F, 8.0000F, -20.0000F, 8.0000F, 3.0000F, 44.0000F)
                .texOffs(99, 171).addBox(-3.0000F, -13.0000F, -16.0000F, 6.0000F, 3.0000F, 6.0000F)
                .texOffs(33, 171).addBox(-4.0000F, -12.0000F, -6.0000F, 8.0000F, 2.0000F, 8.0000F)
                .texOffs(124, 171).addBox(-2.5000F, -12.6000F, -14.0000F, 1.0000F, 1.0000F, 8.0000F)
                .texOffs(143, 171).addBox(1.5000F, -12.6000F, -14.0000F, 1.0000F, 1.0000F, 8.0000F)
                .texOffs(66, 171).addBox(-4.0000F, -12.0000F, 14.0000F, 8.0000F, 2.0000F, 8.0000F)
                .texOffs(162, 171).addBox(-2.5000F, -12.6000F, 6.0000F, 1.0000F, 1.0000F, 8.0000F)
                .texOffs(181, 171).addBox(1.5000F, -12.6000F, 6.0000F, 1.0000F, 1.0000F, 8.0000F)
                .texOffs(133, 154).addBox(-14.5000F, -4.0000F, -20.0000F, 1.0000F, 5.0000F, 10.0000F)
                .texOffs(156, 154).addBox(13.5000F, -4.0000F, -20.0000F, 1.0000F, 5.0000F, 10.0000F)
                .texOffs(0, 154).addBox(-14.5000F, -3.0000F, 2.0000F, 1.0000F, 4.0000F, 12.0000F)
                .texOffs(27, 154).addBox(13.5000F, -3.0000F, 2.0000F, 1.0000F, 4.0000F, 12.0000F)
                .texOffs(179, 154).addBox(-8.0000F, -10.6000F, 22.0000F, 4.0000F, 1.0000F, 14.0000F)
                .texOffs(216, 154).addBox(4.0000F, -6.6000F, -28.0000F, 4.0000F, 1.0000F, 12.0000F)
                .texOffs(0, 79).addBox(-14.3000F, -1.0000F, -24.0000F, 1.0000F, 1.0000F, 46.0000F)
                .texOffs(95, 79).addBox(13.3000F, -1.0000F, -24.0000F, 1.0000F, 1.0000F, 46.0000F)
                .texOffs(14, 183).addBox(-6.0000F, 7.6000F, -12.0000F, 12.0000F, 1.0000F, 2.0000F)
                .texOffs(43, 183).addBox(-6.0000F, 7.6000F, 6.0000F, 12.0000F, 1.0000F, 2.0000F),
                PartPose.offset(0.0000F, 0.0000F, 0.0000F));
        PartDefinition p_prow = root.addOrReplaceChild("prow",
                CubeListBuilder.create()
                .texOffs(218, 127).addBox(-11.0000F, -5.0000F, -14.0000F, 22.0000F, 11.0000F, 14.0000F)
                .texOffs(402, 127).addBox(-7.0000F, -3.0000F, -26.0000F, 14.0000F, 7.0000F, 12.0000F)
                .texOffs(249, 154).addBox(-3.0000F, -1.0000F, -34.0000F, 6.0000F, 4.0000F, 8.0000F)
                .texOffs(205, 171).addBox(-2.0000F, 4.0000F, -22.0000F, 4.0000F, 3.0000F, 5.0000F)
                .texOffs(278, 154).addBox(-1.0000F, -3.0000F, -39.0000F, 2.0000F, 6.0000F, 6.0000F)
                .texOffs(334, 171).addBox(-6.0000F, 0.0000F, -29.0000F, 3.0000F, 3.0000F, 4.0000F)
                .texOffs(349, 171).addBox(3.0000F, 0.0000F, -29.0000F, 3.0000F, 3.0000F, 4.0000F),
                PartPose.offset(0.0000F, 0.0000F, -30.0000F));
        PartDefinition p_bridge = root.addOrReplaceChild("bridge",
                CubeListBuilder.create()
                .texOffs(291, 127).addBox(-7.0000F, -8.0000F, -6.0000F, 14.0000F, 8.0000F, 12.0000F)
                .texOffs(295, 154).addBox(-5.0000F, -12.0000F, -4.0000F, 10.0000F, 4.0000F, 8.0000F)
                .texOffs(200, 171).addBox(-3.0000F, -20.0000F, 2.0000F, 1.0000F, 8.0000F, 1.0000F)
                .texOffs(364, 171).addBox(2.0000F, -18.0000F, 3.0000F, 1.0000F, 6.0000F, 1.0000F)
                .texOffs(470, 171).addBox(-6.0000F, -16.0000F, 5.0000F, 4.0000F, 4.0000F, 1.0000F)
                .texOffs(9, 183).addBox(-4.5000F, -13.0000F, 4.0000F, 1.0000F, 3.0000F, 1.0000F)
                .texOffs(72, 183).addBox(-6.0000F, -3.4000F, -6.3000F, 12.0000F, 1.0000F, 1.0000F),
                PartPose.offset(0.0000F, -10.0000F, 18.0000F));
        PartDefinition p_wing_l = root.addOrReplaceChild("wing_l",
                CubeListBuilder.create()
                .texOffs(0, 127).addBox(0.0000F, 0.0000F, -8.0000F, 30.0000F, 2.0000F, 24.0000F)
                .texOffs(344, 127).addBox(28.0000F, -6.0000F, 4.0000F, 2.0000F, 8.0000F, 12.0000F)
                .texOffs(99, 183).addBox(0.0000F, -0.4000F, -8.6000F, 30.0000F, 1.0000F, 1.0000F)
                .texOffs(481, 171).addBox(8.0000F, 2.0000F, 2.0000F, 1.0000F, 2.0000F, 3.0000F)
                .texOffs(490, 171).addBox(18.0000F, 2.0000F, 2.0000F, 1.0000F, 2.0000F, 3.0000F)
                .texOffs(332, 154).addBox(7.5000F, 4.0000F, -3.0000F, 2.0000F, 2.0000F, 10.0000F)
                .texOffs(357, 154).addBox(17.5000F, 4.0000F, -3.0000F, 2.0000F, 2.0000F, 10.0000F)
                .texOffs(224, 171).addBox(27.0000F, -2.0000F, -4.0000F, 2.0000F, 2.0000F, 6.0000F),
                PartPose.offsetAndRotation(14.0000F, -2.0000F, 6.0000F, 0.0000F, 0.3840F, 0.1396F));
        PartDefinition p_wing_r = root.addOrReplaceChild("wing_r",
                CubeListBuilder.create()
                .texOffs(109, 127).addBox(-30.0000F, 0.0000F, -8.0000F, 30.0000F, 2.0000F, 24.0000F)
                .texOffs(373, 127).addBox(-30.0000F, -6.0000F, 4.0000F, 2.0000F, 8.0000F, 12.0000F)
                .texOffs(162, 183).addBox(-30.0000F, -0.4000F, -8.6000F, 30.0000F, 1.0000F, 1.0000F)
                .texOffs(499, 171).addBox(-9.0000F, 2.0000F, 2.0000F, 1.0000F, 2.0000F, 3.0000F)
                .texOffs(0, 183).addBox(-19.0000F, 2.0000F, 2.0000F, 1.0000F, 2.0000F, 3.0000F)
                .texOffs(382, 154).addBox(-9.5000F, 4.0000F, -3.0000F, 2.0000F, 2.0000F, 10.0000F)
                .texOffs(407, 154).addBox(-19.5000F, 4.0000F, -3.0000F, 2.0000F, 2.0000F, 10.0000F)
                .texOffs(241, 171).addBox(-29.0000F, -2.0000F, -4.0000F, 2.0000F, 2.0000F, 6.0000F),
                PartPose.offsetAndRotation(-14.0000F, -2.0000F, 6.0000F, 0.0000F, -0.3840F, -0.1396F));
        PartDefinition p_nacelle_l = root.addOrReplaceChild("nacelle_l",
                CubeListBuilder.create()
                .texOffs(295, 79).addBox(-4.0000F, -4.0000F, -10.0000F, 8.0000F, 8.0000F, 26.0000F)
                .texOffs(432, 154).addBox(-5.0000F, -5.0000F, -12.0000F, 10.0000F, 10.0000F, 2.0000F)
                .texOffs(258, 171).addBox(-3.0000F, -3.0000F, 16.0000F, 6.0000F, 6.0000F, 2.0000F)
                .texOffs(275, 171).addBox(-5.5000F, -3.0000F, -6.0000F, 1.0000F, 6.0000F, 2.0000F)
                .texOffs(282, 171).addBox(-5.5000F, -3.0000F, 0.0000F, 1.0000F, 6.0000F, 2.0000F)
                .texOffs(289, 171).addBox(-5.5000F, -3.0000F, 6.0000F, 1.0000F, 6.0000F, 2.0000F)
                .texOffs(369, 171).addBox(-1.0000F, -1.0000F, -16.0000F, 2.0000F, 2.0000F, 5.0000F),
                PartPose.offset(16.0000F, 2.0000F, 26.0000F));
        PartDefinition p_nacelle_r = root.addOrReplaceChild("nacelle_r",
                CubeListBuilder.create()
                .texOffs(364, 79).addBox(-4.0000F, -4.0000F, -10.0000F, 8.0000F, 8.0000F, 26.0000F)
                .texOffs(457, 154).addBox(-5.0000F, -5.0000F, -12.0000F, 10.0000F, 10.0000F, 2.0000F)
                .texOffs(296, 171).addBox(-3.0000F, -3.0000F, 16.0000F, 6.0000F, 6.0000F, 2.0000F)
                .texOffs(313, 171).addBox(4.5000F, -3.0000F, -6.0000F, 1.0000F, 6.0000F, 2.0000F)
                .texOffs(320, 171).addBox(4.5000F, -3.0000F, 0.0000F, 1.0000F, 6.0000F, 2.0000F)
                .texOffs(327, 171).addBox(4.5000F, -3.0000F, 6.0000F, 1.0000F, 6.0000F, 2.0000F)
                .texOffs(384, 171).addBox(-1.0000F, -1.0000F, -16.0000F, 2.0000F, 2.0000F, 5.0000F),
                PartPose.offset(-16.0000F, 2.0000F, 26.0000F));
        PartDefinition p_pod_l = root.addOrReplaceChild("pod_l",
                CubeListBuilder.create()
                .texOffs(54, 154).addBox(0.0000F, 0.0000F, -6.0000F, 3.0000F, 4.0000F, 12.0000F),
                PartPose.offset(15.0000F, -8.0000F, -8.0000F));
        PartDefinition p_pod_r = root.addOrReplaceChild("pod_r",
                CubeListBuilder.create()
                .texOffs(85, 154).addBox(0.0000F, 0.0000F, -6.0000F, 3.0000F, 4.0000F, 12.0000F),
                PartPose.offset(-18.0000F, -8.0000F, -8.0000F));
        PartDefinition p_cannon = root.addOrReplaceChild("cannon",
                CubeListBuilder.create()
                .texOffs(0, 171).addBox(-4.0000F, -1.0000F, -4.0000F, 8.0000F, 3.0000F, 8.0000F),
                PartPose.offset(0.0000F, 8.0000F, -6.0000F));
        PartDefinition p_cannon_barrel = p_cannon.addOrReplaceChild("cannon_barrel",
                CubeListBuilder.create()
                .texOffs(116, 154).addBox(-2.0000F, 0.0000F, -2.0000F, 4.0000F, 12.0000F, 4.0000F)
                .texOffs(399, 171).addBox(-2.5000F, 12.0000F, -2.5000F, 5.0000F, 2.0000F, 5.0000F)
                .texOffs(420, 171).addBox(-3.0000F, 3.0000F, -3.0000F, 6.0000F, 1.0000F, 6.0000F)
                .texOffs(445, 171).addBox(-3.0000F, 7.0000F, -3.0000F, 6.0000F, 1.0000F, 6.0000F),
                PartPose.offset(0.0000F, -6.0000F, 6.0000F));
        return LayerDefinition.create(mesh, 512, 512);
    }
}
