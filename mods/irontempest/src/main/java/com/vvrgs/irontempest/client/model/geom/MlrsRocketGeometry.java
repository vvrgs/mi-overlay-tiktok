// GENERADO por tools/gen_models.py — NO editar a mano; edita el spec y regenera.
package com.vvrgs.irontempest.client.model.geom;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class MlrsRocketGeometry {
    private MlrsRocketGeometry() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5000F, -1.5000F, -7.0000F, 3.0000F, 3.0000F, 14.0000F),
                PartPose.offset(0.0000F, 0.0000F, 0.0000F));
        PartDefinition p_nose = root.addOrReplaceChild("nose",
                CubeListBuilder.create()
                .texOffs(9, 18).addBox(-1.0000F, -1.0000F, -3.0000F, 2.0000F, 2.0000F, 3.0000F)
                .texOffs(29, 18).addBox(-0.5000F, -0.5000F, -5.0000F, 1.0000F, 1.0000F, 2.0000F),
                PartPose.offset(0.0000F, 0.0000F, -7.0000F));
        PartDefinition p_rfin0 = root.addOrReplaceChild("rfin0",
                CubeListBuilder.create()
                .texOffs(35, 0).addBox(-0.5000F, -4.0000F, 0.0000F, 1.0000F, 4.0000F, 3.0000F),
                PartPose.offset(0.0000F, 0.0000F, 4.0000F));
        PartDefinition p_rfin1 = root.addOrReplaceChild("rfin1",
                CubeListBuilder.create()
                .texOffs(44, 0).addBox(-0.5000F, -4.0000F, 0.0000F, 1.0000F, 4.0000F, 3.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, 4.0000F, 0.0000F, 0.0000F, 1.5708F));
        PartDefinition p_rfin2 = root.addOrReplaceChild("rfin2",
                CubeListBuilder.create()
                .texOffs(53, 0).addBox(-0.5000F, -4.0000F, 0.0000F, 1.0000F, 4.0000F, 3.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, 4.0000F, 0.0000F, 0.0000F, 3.1416F));
        PartDefinition p_rfin3 = root.addOrReplaceChild("rfin3",
                CubeListBuilder.create()
                .texOffs(0, 18).addBox(-0.5000F, -4.0000F, 0.0000F, 1.0000F, 4.0000F, 3.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, 4.0000F, 0.0000F, 0.0000F, 4.7124F));
        PartDefinition p_rnozzle = root.addOrReplaceChild("rnozzle",
                CubeListBuilder.create()
                .texOffs(20, 18).addBox(-1.0000F, -1.0000F, 0.0000F, 2.0000F, 2.0000F, 2.0000F),
                PartPose.offset(0.0000F, 0.0000F, 7.0000F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
