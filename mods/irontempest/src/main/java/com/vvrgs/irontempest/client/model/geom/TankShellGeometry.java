// GENERADO por tools/gen_models.py — NO editar a mano; edita el spec y regenera.
package com.vvrgs.irontempest.client.model.geom;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class TankShellGeometry {
    private TankShellGeometry() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_shell = root.addOrReplaceChild("shell",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0000F, -1.0000F, -3.0000F, 2.0000F, 2.0000F, 7.0000F)
                .texOffs(19, 0).addBox(-0.5000F, -0.5000F, -5.0000F, 1.0000F, 1.0000F, 2.0000F),
                PartPose.offset(0.0000F, 0.0000F, 0.0000F));
        return LayerDefinition.create(mesh, 32, 32);
    }
}
