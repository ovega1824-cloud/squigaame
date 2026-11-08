package com.squidgame.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee un modelo Bedrock (.geo.json) y su animación (.animation.json) y lo dibuja sin librerías externas.
 * Trabajamos en el espacio del modelo de Java (y hacia abajo): y' = 24 - y.
 */
public class FinalModel {
    private static final float DEG = (float) (Math.PI / 180.0D);
    private static final ResourceLocation GEO = new ResourceLocation("squidgame", "geo/final.geo.json");
    private static final ResourceLocation ANIM = new ResourceLocation("squidgame", "animations/final.animation.json");
    public static final ResourceLocation TEXTURE = new ResourceLocation("squidgame", "textures/entity/final.png");

    private static FinalModel instance;
    private static boolean tried = false;

    public static synchronized FinalModel get() {
        if (!tried) {
            tried = true;
            try {
                instance = new FinalModel();
            } catch (Throwable t) {
                LogUtils.getLogger().error("No se pudo cargar el modelo de la final", t);
                instance = null;
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ datos

    private static final int N = 0, S = 1, E = 2, W = 3, UP = 4, DOWN = 5;

    private static class Face {
        float u, v, w, h;

        Face(float u, float v, float w, float h) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
        }
    }

    private static class Cube {
        float x0, y0, z0, x1, y1, z1;
        boolean rotated;
        float pvx, pvy, pvz, rx, ry, rz;
        final Face[] faces = new Face[6];

        static Cube box(float x, float yTop, float z, float w, float h, float d, float inflate, float u, float v) {
            Cube c = new Cube();
            c.x0 = x - inflate;
            c.x1 = x + w + inflate;
            c.y0 = yTop - inflate;
            c.y1 = yTop + h + inflate;
            c.z0 = z - inflate;
            c.z1 = z + d + inflate;
            c.boxUv(u, v, w, h, d);
            return c;
        }

        void boxUv(float u, float v, float w, float h, float d) {
            faces[E] = new Face(u, v + d, d, h);
            faces[N] = new Face(u + d, v + d, w, h);
            faces[W] = new Face(u + d + w, v + d, d, h);
            faces[S] = new Face(u + 2 * d + w, v + d, w, h);
            faces[UP] = new Face(u + d, v, w, d);
            faces[DOWN] = new Face(u + d + w, v + d, w, -d);
        }
    }

    private static class Bone {
        String name;
        Bone parent;
        float px, py, pz;      // pivote absoluto (espacio Java)
        float rx, ry, rz;      // rotación del modelo (grados)
        final List<Cube> cubes = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();
        // animación actual
        float ax, ay, az, tx, ty, tz;
    }

    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final List<Bone> roots = new ArrayList<>();
    private int texW = 64, texH = 64;
    private final Map<String, float[]> animRot = new HashMap<>();
    private final Map<String, float[]> animPos = new HashMap<>();
    private final List<Cube> headCubes = new ArrayList<>();

    private FinalModel() throws Exception {
        loadGeo();
        loadAnim();
        headCubes.add(Cube.box(-4, -8, -4, 8, 8, 8, 0.0F, 0, 0));
        headCubes.add(Cube.box(-4, -8, -4, 8, 8, 8, 0.5F, 32, 0));
    }

    private static JsonObject readJson(ResourceLocation loc) throws Exception {
        var res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) throw new IllegalStateException("Falta el recurso " + loc);
        try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static float[] arr3(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private void loadGeo() throws Exception {
        JsonObject root = readJson(GEO);
        JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject desc = geo.getAsJsonObject("description");
        if (desc.has("texture_width")) texW = desc.get("texture_width").getAsInt();
        if (desc.has("texture_height")) texH = desc.get("texture_height").getAsInt();

        List<String> parents = new ArrayList<>();
        for (JsonElement be : geo.getAsJsonArray("bones")) {
            JsonObject bo = be.getAsJsonObject();
            Bone b = new Bone();
            b.name = bo.get("name").getAsString();
            float[] pv = bo.has("pivot") ? arr3(bo.get("pivot")) : new float[]{0, 0, 0};
            b.px = pv[0];
            b.py = 24.0F - pv[1];
            b.pz = pv[2];
            if (bo.has("rotation")) {
                float[] r = arr3(bo.get("rotation"));
                b.rx = r[0];
                b.ry = r[1];
                b.rz = r[2];
            }
            if (bo.has("cubes")) {
                for (JsonElement ce : bo.getAsJsonArray("cubes")) b.cubes.add(parseCube(ce.getAsJsonObject()));
            }
            bones.put(b.name, b);
            parents.add(bo.has("parent") ? bo.get("parent").getAsString() : null);
        }
        int i = 0;
        for (Bone b : bones.values()) {
            String pn = parents.get(i++);
            Bone parent = pn == null ? null : bones.get(pn);
            b.parent = parent;
            if (parent == null) roots.add(b);
            else parent.children.add(b);
        }
    }

    private Cube parseCube(JsonObject co) {
        float[] o = arr3(co.get("origin"));
        float[] s = arr3(co.get("size"));
        float inf = co.has("inflate") ? co.get("inflate").getAsFloat() : 0.0F;
        Cube c = new Cube();
        c.x0 = o[0] - inf;
        c.x1 = o[0] + s[0] + inf;
        c.y0 = 24.0F - (o[1] + s[1] + inf);
        c.y1 = 24.0F - (o[1] - inf);
        c.z0 = o[2] - inf;
        c.z1 = o[2] + s[2] + inf;
        if (co.has("rotation")) {
            float[] r = arr3(co.get("rotation"));
            float[] pv = co.has("pivot") ? arr3(co.get("pivot")) : new float[]{0, 0, 0};
            c.rotated = true;
            c.pvx = pv[0];
            c.pvy = 24.0F - pv[1];
            c.pvz = pv[2];
            c.rx = r[0];
            c.ry = r[1];
            c.rz = r[2];
        }
        JsonElement uv = co.get("uv");
        if (uv != null && uv.isJsonArray()) {
            JsonArray a = uv.getAsJsonArray();
            c.boxUv(a.get(0).getAsFloat(), a.get(1).getAsFloat(), s[0], s[1], s[2]);
        } else if (uv != null && uv.isJsonObject()) {
            JsonObject u = uv.getAsJsonObject();
            c.faces[N] = face(u, "north");
            c.faces[S] = face(u, "south");
            c.faces[E] = face(u, "east");   // la cara del lado -X (primer bloque de la textura)
            c.faces[W] = face(u, "west");   // la cara del lado +X
            c.faces[UP] = face(u, "up");
            c.faces[DOWN] = face(u, "down");
        }
        return c;
    }

    private static Face face(JsonObject uv, String name) {
        if (!uv.has(name)) return null;
        JsonObject f = uv.getAsJsonObject(name);
        float[] a = f.get("uv").getAsJsonArray().size() >= 2
                ? new float[]{f.getAsJsonArray("uv").get(0).getAsFloat(), f.getAsJsonArray("uv").get(1).getAsFloat()}
                : new float[]{0, 0};
        float w = 1, h = 1;
        if (f.has("uv_size")) {
            w = f.getAsJsonArray("uv_size").get(0).getAsFloat();
            h = f.getAsJsonArray("uv_size").get(1).getAsFloat();
        }
        return new Face(a[0], a[1], w, h);
    }

    private void loadAnim() throws Exception {
        JsonObject root = readJson(ANIM);
        JsonObject anims = root.getAsJsonObject("animations");
        for (String key : anims.keySet()) {            // se usa la primera animación ("tirado ")
            JsonObject bonesObj = anims.getAsJsonObject(key).getAsJsonObject("bones");
            for (String bn : bonesObj.keySet()) {
                JsonObject b = bonesObj.getAsJsonObject(bn);
                if (b.has("rotation")) animRot.put(bn, vec(b.get("rotation")));
                if (b.has("position")) animPos.put(bn, vec(b.get("position")));
            }
            break;
        }
    }

    private static float[] vec(JsonElement e) {
        if (e.isJsonObject() && e.getAsJsonObject().has("vector")) return arr3(e.getAsJsonObject().get("vector"));
        return arr3(e);
    }

    // ------------------------------------------------------------------ dibujo

    /**
     * @param down     0 = de pie, 1 = tirado en el suelo (animación completa)
     * @param limbSwing/limbAmount animación de caminar
     */
    public void render(PoseStack ps, MultiBufferSource buffers, ResourceLocation skin, int light, int overlay,
                       float down, float limbSwing, float limbAmount, float headYaw, float headPitch) {
        // reiniciar y aplicar la animación
        for (Bone b : bones.values()) {
            b.ax = b.ay = b.az = b.tx = b.ty = b.tz = 0.0F;
            float[] r = animRot.get(b.name);
            if (r != null) {
                b.ax = r[0] * down;
                b.ay = r[1] * down;
                b.az = r[2] * down;
            }
            float[] p = animPos.get(b.name);
            if (p != null) {
                b.tx = p[0] * down;
                b.ty = -p[1] * down;   // Bedrock: y hacia arriba; aquí y hacia abajo
                b.tz = p[2] * down;
            }
        }
        float walk = (1.0F - down) * limbAmount;
        if (walk > 0.0F) {
            float a = limbSwing * 0.6662F;
            add("right_arm", (float) Math.cos(a + Math.PI) * 1.0F * walk / DEG * 1.0F);
            add("left_arm", (float) Math.cos(a) * 1.0F * walk / DEG * 1.0F);
            add("right_leg", (float) Math.cos(a) * 1.4F * walk / DEG);
            add("left_leg", (float) Math.cos(a + Math.PI) * 1.4F * walk / DEG);
        }

        VertexConsumer suit = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        VertexConsumer head = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        for (Bone b : roots) renderBone(ps, b, null, suit, head, light, overlay, headYaw, headPitch);
    }

    private void add(String bone, float degX) {
        Bone b = bones.get(bone);
        if (b != null) b.ax += degX;
    }

    private void renderBone(PoseStack ps, Bone b, Bone parent, VertexConsumer suit, VertexConsumer head,
                            int light, int overlay, float headYaw, float headPitch) {
        ps.pushPose();
        float ppx = parent == null ? 0 : parent.px;
        float ppy = parent == null ? 0 : parent.py;
        float ppz = parent == null ? 0 : parent.pz;
        ps.translate((b.px - ppx + b.tx) / 16.0F, (b.py - ppy + b.ty) / 16.0F, (b.pz - ppz + b.tz) / 16.0F);
        float rx = b.rx + b.ax, ry = b.ry + b.ay, rz = b.rz + b.az;
        if (rx != 0 || ry != 0 || rz != 0) {
            ps.mulPose(new Quaternionf().rotationZYX(rz * DEG, ry * DEG, rx * DEG));
        }
        for (Cube c : b.cubes) drawCube(ps, c, b.px, b.py, b.pz, suit, light, overlay);

        if (b.name.equals("body_upper")) {
            // la cabeza (con la skin del jugador) cuelga de la parte alta del torso
            ps.pushPose();
            ps.translate((0 - b.px) / 16.0F, (0 - b.py) / 16.0F, (0 - b.pz) / 16.0F);
            ps.mulPose(new Quaternionf().rotationZYX(0.0F, headYaw * DEG, headPitch * DEG));
            for (Cube c : headCubes) drawCube(ps, c, 0, 0, 0, head, light, overlay);
            ps.popPose();
        }

        for (Bone ch : b.children) renderBone(ps, ch, b, suit, head, light, overlay, headYaw, headPitch);
        ps.popPose();
    }

    private void drawCube(PoseStack ps, Cube c, float refX, float refY, float refZ,
                          VertexConsumer vc, int light, int overlay) {
        ps.pushPose();
        if (c.rotated) {
            ps.translate((c.pvx - refX) / 16.0F, (c.pvy - refY) / 16.0F, (c.pvz - refZ) / 16.0F);
            ps.mulPose(new Quaternionf().rotationZYX(c.rz * DEG, c.ry * DEG, c.rx * DEG));
            refX = c.pvx;
            refY = c.pvy;
            refZ = c.pvz;
        }
        PoseStack.Pose pose = ps.last();
        float x0 = c.x0, x1 = c.x1, y0 = c.y0, y1 = c.y1, z0 = c.z0, z1 = c.z1;
        float dx = x1 - x0 == 0 ? 1 : x1 - x0;
        float dy = y1 - y0 == 0 ? 1 : y1 - y0;
        float dz = z1 - z0 == 0 ? 1 : z1 - z0;

        Face f = c.faces[N];
        if (f != null) {
            final Face ff = f;
            float[][] p = {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
            quad(pose, vc, p, refX, refY, refZ, light, overlay, 0, 0, -1, (q, i) -> {
                q[0] = ff.u + ff.w * (p[i][0] - x0) / dx;
                q[1] = ff.v + ff.h * (p[i][1] - y0) / dy;
            });
        }
        f = c.faces[S];
        if (f != null) {
            final Face ff = f;
            float[][] p = {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            quad(pose, vc, p, refX, refY, refZ, light, overlay, 0, 0, 1, (q, i) -> {
                q[0] = ff.u + ff.w * (x1 - p[i][0]) / dx;
                q[1] = ff.v + ff.h * (p[i][1] - y0) / dy;
            });
        }
        f = c.faces[E];
        if (f != null) {
            final Face ff = f;
            float[][] p = {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            quad(pose, vc, p, refX, refY, refZ, light, overlay, -1, 0, 0, (q, i) -> {
                q[0] = ff.u + ff.w * (z1 - p[i][2]) / dz;
                q[1] = ff.v + ff.h * (p[i][1] - y0) / dy;
            });
        }
        f = c.faces[W];
        if (f != null) {
            final Face ff = f;
            float[][] p = {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
            quad(pose, vc, p, refX, refY, refZ, light, overlay, 1, 0, 0, (q, i) -> {
                q[0] = ff.u + ff.w * (p[i][2] - z0) / dz;
                q[1] = ff.v + ff.h * (p[i][1] - y0) / dy;
            });
        }
        f = c.faces[UP];
        if (f != null) {
            final Face ff = f;
            float[][] p = {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
            quad(pose, vc, p, refX, refY, refZ, light, overlay, 0, -1, 0, (q, i) -> {
                q[0] = ff.u + ff.w * (p[i][0] - x0) / dx;
                q[1] = ff.v + ff.h * (z1 - p[i][2]) / dz;
            });
        }
        f = c.faces[DOWN];
        if (f != null) {
            final Face ff = f;
            float[][] p = {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            quad(pose, vc, p, refX, refY, refZ, light, overlay, 0, 1, 0, (q, i) -> {
                q[0] = ff.u + ff.w * (p[i][0] - x0) / dx;
                q[1] = ff.v + ff.h * (p[i][2] - z0) / dz;
            });
        }
        ps.popPose();
    }

    private interface UvFn {
        void uv(float[] out, int corner);
    }

    private void quad(PoseStack.Pose pose, VertexConsumer vc, float[][] p, float rx, float ry, float rz,
                      int light, int overlay, float nx, float ny, float nz, UvFn fn) {
        float[] uv = new float[2];
        for (int i = 0; i < 4; i++) {
            fn.uv(uv, i);
            vc.vertex(pose.pose(), (p[i][0] - rx) / 16.0F, (p[i][1] - ry) / 16.0F, (p[i][2] - rz) / 16.0F)
                    .color(255, 255, 255, 255)
                    .uv(uv[0] / texW, uv[1] / texH)
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(pose.normal(), nx, ny, nz)
                    .endVertex();
        }
    }
}
