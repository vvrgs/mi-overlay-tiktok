// GENERADO por tools/gen_models.py — NO editar a mano; edita el spec y regenera.
package com.vvrgs.irontempest.client.model.geom;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class CruiseMissileGeometry {
    private CruiseMissileGeometry() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0000F, -3.0000F, -22.0000F, 6.0000F, 6.0000F, 40.0000F)
                .texOffs(52, 47).addBox(-2.0000F, 3.0000F, 4.0000F, 4.0000F, 2.0000F, 10.0000F)
                .texOffs(186, 0).addBox(-0.5000F, -3.8000F, -16.0000F, 1.0000F, 1.0000F, 28.0000F)
                .texOffs(146, 47).addBox(-3.5000F, -3.5000F, -8.0000F, 7.0000F, 7.0000F, 1.0000F)
                .texOffs(163, 47).addBox(-3.5000F, -3.5000F, 8.0000F, 7.0000F, 7.0000F, 1.0000F)
                .texOffs(212, 47).addBox(2.8000F, -1.0000F, 2.0000F, 1.0000F, 2.0000F, 3.0000F)
                .texOffs(239, 47).addBox(-3.4000F, -0.5000F, -20.0000F, 1.0000F, 1.0000F, 2.0000F)
                .texOffs(246, 47).addBox(2.4000F, -0.5000F, -20.0000F, 1.0000F, 1.0000F, 2.0000F)
                .texOffs(0, 63).addBox(-0.5000F, -3.4000F, -20.0000F, 1.0000F, 1.0000F, 2.0000F)
                .texOffs(7, 63).addBox(-0.5000F, 2.4000F, -20.0000F, 1.0000F, 1.0000F, 2.0000F),
                PartPose.offset(0.0000F, 0.0000F, 0.0000F));
        PartDefinition p_body_oct = root.addOrReplaceChild("body_oct",
                CubeListBuilder.create()
                .texOffs(93, 0).addBox(-3.0000F, -3.0000F, -22.0000F, 6.0000F, 6.0000F, 40.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, 0.0000F, 0.0000F, 0.0000F, 0.7854F));
        PartDefinition p_nose = root.addOrReplaceChild("nose",
                CubeListBuilder.create()
                .texOffs(125, 47).addBox(-2.5000F, -2.5000F, -5.0000F, 5.0000F, 5.0000F, 5.0000F)
                .texOffs(197, 47).addBox(-1.5000F, -1.5000F, -9.0000F, 3.0000F, 3.0000F, 4.0000F)
                .texOffs(221, 47).addBox(-0.5000F, -0.5000F, -12.0000F, 1.0000F, 1.0000F, 3.0000F),
                PartPose.offset(0.0000F, 0.0000F, -22.0000F));
        PartDefinition p_fin0 = root.addOrReplaceChild("fin0",
                CubeListBuilder.create()
                .texOffs(0, 47).addBox(-0.5000F, -10.0000F, 0.0000F, 1.0000F, 10.0000F, 5.0000F),
                PartPose.offset(0.0000F, 0.0000F, 14.0000F));
        PartDefinition p_fin1 = root.addOrReplaceChild("fin1",
                CubeListBuilder.create()
                .texOffs(13, 47).addBox(-0.5000F, -10.0000F, 0.0000F, 1.0000F, 10.0000F, 5.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, 14.0000F, 0.0000F, 0.0000F, 1.5708F));
        PartDefinition p_fin2 = root.addOrReplaceChild("fin2",
                CubeListBuilder.create()
                .texOffs(26, 47).addBox(-0.5000F, -10.0000F, 0.0000F, 1.0000F, 10.0000F, 5.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, 14.0000F, 0.0000F, 0.0000F, 3.1416F));
        PartDefinition p_fin3 = root.addOrReplaceChild("fin3",
                CubeListBuilder.create()
                .texOffs(39, 47).addBox(-0.5000F, -10.0000F, 0.0000F, 1.0000F, 10.0000F, 5.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, 14.0000F, 0.0000F, 0.0000F, 4.7124F));
        PartDefinition p_midfin0 = root.addOrReplaceChild("midfin0",
                CubeListBuilder.create()
                .texOffs(81, 47).addBox(-0.5000F, -7.0000F, 0.0000F, 1.0000F, 7.0000F, 4.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, -6.0000F, 0.0000F, 0.0000F, 0.7854F));
        PartDefinition p_midfin1 = root.addOrReplaceChild("midfin1",
                CubeListBuilder.create()
                .texOffs(92, 47).addBox(-0.5000F, -7.0000F, 0.0000F, 1.0000F, 7.0000F, 4.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, -6.0000F, 0.0000F, 0.0000F, 2.3562F));
        PartDefinition p_midfin2 = root.addOrReplaceChild("midfin2",
                CubeListBuilder.create()
                .texOffs(103, 47).addBox(-0.5000F, -7.0000F, 0.0000F, 1.0000F, 7.0000F, 4.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, -6.0000F, 0.0000F, 0.0000F, 3.9270F));
        PartDefinition p_midfin3 = root.addOrReplaceChild("midfin3",
                CubeListBuilder.create()
                .texOffs(114, 47).addBox(-0.5000F, -7.0000F, 0.0000F, 1.0000F, 7.0000F, 4.0000F),
                PartPose.offsetAndRotation(0.0000F, 0.0000F, -6.0000F, 0.0000F, 0.0000F, 5.4978F));
        PartDefinition p_nozzle = root.addOrReplaceChild("nozzle",
                CubeListBuilder.create()
                .texOffs(180, 47).addBox(-2.5000F, -2.5000F, 0.0000F, 5.0000F, 5.0000F, 3.0000F)
                .texOffs(230, 47).addBox(-1.5000F, -1.5000F, 2.5000F, 3.0000F, 3.0000F, 1.0000F),
                PartPose.offset(0.0000F, 0.0000F, 18.0000F));
        return LayerDefinition.create(mesh, 256, 256);
    }
}
