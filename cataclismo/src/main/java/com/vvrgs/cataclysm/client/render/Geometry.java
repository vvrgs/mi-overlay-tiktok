package com.vvrgs.cataclysm.client.render;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERADO por tools/specs.py — NO editar a mano.
 * Fuente unica de verdad: SPECS (python) => Geometry.java + atlas pintados.
 */
public final class Geometry {

    public static LayerDefinition tornadoLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(0.0F, 84.0F, 0.0F));
        root.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 12.0F, 7.0F), PartPose.offset(0.0F, 72.0F, 0.0F));
        root.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(44, 0).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 12.0F, 10.0F), PartPose.offset(0.0F, 60.0F, 0.0F));
        root.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 22).addBox(-6.5F, 0.0F, -6.5F, 13.0F, 12.0F, 13.0F), PartPose.offset(0.0F, 48.0F, 0.0F));
        root.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(52, 22).addBox(-8.0F, 0.0F, -8.0F, 16.0F, 12.0F, 16.0F), PartPose.offset(0.0F, 36.0F, 0.0F));
        root.addOrReplaceChild("seg5", CubeListBuilder.create().texOffs(0, 50).addBox(-9.5F, 0.0F, -9.5F, 19.0F, 12.0F, 19.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("seg6", CubeListBuilder.create().texOffs(0, 81).addBox(-11.0F, 0.0F, -11.0F, 22.0F, 12.0F, 22.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("seg7", CubeListBuilder.create().texOffs(0, 115).addBox(-12.5F, 0.0F, -12.5F, 25.0F, 12.0F, 25.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 256);
    }

    public static LayerDefinition bolideLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -5.0F, -5.0F, 10.0F, 10.0F, 10.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("shard0", CubeListBuilder.create().texOffs(40, 0).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.400F, 0.300F, 0.000F));
        root.addOrReplaceChild("shard1", CubeListBuilder.create().texOffs(0, 20).addBox(-8.0F, -2.0F, -2.0F, 6.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.000F, 0.500F, 0.400F));
        root.addOrReplaceChild("shard2", CubeListBuilder.create().texOffs(20, 20).addBox(-2.0F, -2.0F, 3.0F, 4.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.300F, 0.000F, 0.500F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition bombLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("plate0", CubeListBuilder.create().texOffs(32, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.200F, 0.600F, 0.100F));
        root.addOrReplaceChild("plate1", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, 4.0F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.150F, 0.200F, -0.200F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition tsunamiLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-30.0F, 8.0F, -6.0F, 60.0F, 80.0F, 12.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("curl", CubeListBuilder.create().texOffs(0, 92).addBox(-30.0F, 0.0F, -16.0F, 60.0F, 12.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.500F, 0.000F, 0.000F));
        root.addOrReplaceChild("foam", CubeListBuilder.create().texOffs(0, 116).addBox(-30.0F, -2.0F, -18.0F, 60.0F, 6.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.700F, 0.000F, 0.000F));
        return LayerDefinition.create(mesh, 256, 128);
    }

    public static LayerDefinition impactorLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 32).addBox(-10.0F, -10.0F, -10.0F, 20.0F, 20.0F, 20.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private Geometry() {
    }
}
