package com.wish.client.features.visual;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.wish.client.color.ColorUtils;
import com.wish.client.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

public final class CatPawTrailManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-CatPawTrails");
    private static final Identifier ATLAS_ID = Identifier.fromNamespaceAndPath("wish", "cat_paw_trail_atlas");

    private static final int MAX_FOOTPRINTS = 64;
    private static final int MAX_SPARKLES = 96;
    private static final int MAX_TRACKED_PLAYERS = 32;
    private static final int MAX_QUADS_PER_FOOTPRINT = 5;
    private static final int VERTICES_PER_QUAD = 4;
    private static final double MIN_STEP_DIST_SQ = 0.25;
    private static final double TELEPORT_DIST_SQ = 9.0;
    private static final double TELEPORT_MAX_DY = 2.5;
    private static final double TRACK_PLAYER_RADIUS_SQ = 32.0 * 32.0;
    private static final double RENDER_FOOTPRINT_MAX_DIST_SQ = 48.0 * 48.0;
    private static final double RENDER_SPARKLE_MAX_DIST_SQ = 40.0 * 40.0;
    private static final float COS_7_DEG = (float) Math.cos(CatPawTrailMath.STEP_TOE_OUT_RAD);
    private static final float SIN_7_DEG = (float) Math.sin(CatPawTrailMath.STEP_TOE_OUT_RAD);
    private static final float RIPPLE_LIFE_SEC = 0.46f;
    private static final float UV_PAD = 0.002f;

    private static final float[] FP_X = new float[MAX_FOOTPRINTS];
    private static final float[] FP_Y = new float[MAX_FOOTPRINTS];
    private static final float[] FP_Z = new float[MAX_FOOTPRINTS];
    private static final float[] FP_FWD_X = new float[MAX_FOOTPRINTS];
    private static final float[] FP_FWD_Z = new float[MAX_FOOTPRINTS];
    private static final float[] FP_RIGHT_X = new float[MAX_FOOTPRINTS];
    private static final float[] FP_RIGHT_Z = new float[MAX_FOOTPRINTS];
    private static final long[] FP_SPAWN_MS = new long[MAX_FOOTPRINTS];
    private static final boolean[] FP_ACTIVE = new boolean[MAX_FOOTPRINTS];

    private static final float[] SP_X = new float[MAX_SPARKLES];
    private static final float[] SP_Y = new float[MAX_SPARKLES];
    private static final float[] SP_Z = new float[MAX_SPARKLES];
    private static final float[] SP_VX = new float[MAX_SPARKLES];
    private static final float[] SP_VY = new float[MAX_SPARKLES];
    private static final float[] SP_VZ = new float[MAX_SPARKLES];
    private static final long[] SP_SPAWN_MS = new long[MAX_SPARKLES];
    private static final float[] SP_LIFE_SEC = new float[MAX_SPARKLES];
    private static final float[] SP_SCALE = new float[MAX_SPARKLES];
    private static final float[] SP_ROT = new float[MAX_SPARKLES];
    private static final float[] SP_SPIN = new float[MAX_SPARKLES];
    private static final boolean[] SP_IS_HEART = new boolean[MAX_SPARKLES];
    private static final boolean[] SP_ACTIVE = new boolean[MAX_SPARKLES];

    private static final int[] TR_ENTITY_ID = new int[MAX_TRACKED_PLAYERS];
    private static final double[] TR_LAST_X = new double[MAX_TRACKED_PLAYERS];
    private static final double[] TR_LAST_Y = new double[MAX_TRACKED_PLAYERS];
    private static final double[] TR_LAST_Z = new double[MAX_TRACKED_PLAYERS];
    private static final boolean[] TR_NEXT_LEFT = new boolean[MAX_TRACKED_PLAYERS];
    private static final long[] TR_LAST_TICK_SEEN = new long[MAX_TRACKED_PLAYERS];

    private static final BlockPos.MutableBlockPos SCRATCH_POS = new BlockPos.MutableBlockPos();
    private static final Vector3f SCRATCH_VEC = new Vector3f();
    private static final Random RNG = new Random();

    private static int fpCursor = 0;
    private static int activeFootprintCount = 0;
    private static int spCursor = 0;
    private static int activeSparkleCount = 0;
    private static long tickCounter = 0L;
    private static ClientLevel lastLevel = null;

    private static CatPawAtlasTexture atlasTexture = null;
    private static GpuBuffer vertexBuffer = null;
    private static boolean atlasFailed = false;

    private static double frameCamX;
    private static double frameCamY;
    private static double frameCamZ;
    private static float frameCamRightX;
    private static float frameCamRightY;
    private static float frameCamRightZ;
    private static float frameCamUpX;
    private static float frameCamUpY;
    private static float frameCamUpZ;
    private static long frameNowMs;

    static {
        Arrays.fill(TR_ENTITY_ID, Integer.MIN_VALUE);
    }

    private CatPawTrailManager() {
    }

    public static void reset() {
        Arrays.fill(FP_ACTIVE, false);
        Arrays.fill(SP_ACTIVE, false);
        Arrays.fill(TR_ENTITY_ID, Integer.MIN_VALUE);
        Arrays.fill(TR_LAST_TICK_SEEN, 0L);
        fpCursor = 0;
        activeFootprintCount = 0;
        spCursor = 0;
        activeSparkleCount = 0;
        tickCounter = 0L;
        lastLevel = null;
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }

    public static void onClientTick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null || !ModConfig.INSTANCE.enableCatPawTrails) {
            if (lastLevel != null || activeFootprintCount > 0 || activeSparkleCount > 0) {
                reset();
            }
            return;
        }
        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }
        if (mc.isPaused()) {
            return;
        }

        tickCounter++;
        long nowMs = System.currentTimeMillis();
        trackPlayerStep(mc.level, mc.player, nowMs);

        if (ModConfig.INSTANCE.catPawTrailIncludeOthers) {
            double selfX = mc.player.getX();
            double selfY = mc.player.getY();
            double selfZ = mc.player.getZ();
            List<? extends Player> players = mc.level.players();
            for (int i = 0; i < players.size(); i++) {
                Player other = players.get(i);
                if (other == null || other == mc.player) {
                    continue;
                }
                double dx = other.getX() - selfX;
                double dy = other.getY() - selfY;
                double dz = other.getZ() - selfZ;
                if (dx * dx + dy * dy + dz * dz > TRACK_PLAYER_RADIUS_SQ) {
                    continue;
                }
                if (RealPlayerFilter.isRealPlayer(other, false)) {
                    trackPlayerStep(mc.level, other, nowMs);
                }
            }
        }
    }

    public static void renderWorld(LevelRenderContext context) {
        if (!ModConfig.INSTANCE.enableCatPawTrails || context == null || context.levelState() == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        float durationSec = clampDuration(ModConfig.INSTANCE.catPawTrailDuration);
        sweepExpired(nowMs, durationSec);
        if (activeFootprintCount == 0 && activeSparkleCount == 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        CameraRenderState camState = context.levelState().cameraRenderState;
        if (mc.level == null || camState == null || !camState.initialized || camState.pos == null || camState.orientation == null) {
            return;
        }
        if (!ensureAtlasLoaded(mc)) {
            return;
        }

        RenderTarget target = ChunkSectionLayerGroup.TRANSLUCENT.outputTarget();
        if (target == null || target.getColorTextureView() == null) {
            return;
        }

        frameCamX = camState.pos.x;
        frameCamY = camState.pos.y;
        frameCamZ = camState.pos.z;
        frameNowMs = nowMs;

        SCRATCH_VEC.set(1.0f, 0.0f, 0.0f).rotate(camState.orientation);
        frameCamRightX = SCRATCH_VEC.x;
        frameCamRightY = SCRATCH_VEC.y;
        frameCamRightZ = SCRATCH_VEC.z;

        SCRATCH_VEC.set(0.0f, 1.0f, 0.0f).rotate(camState.orientation);
        frameCamUpX = SCRATCH_VEC.x;
        frameCamUpY = SCRATCH_VEC.y;
        frameCamUpZ = SCRATCH_VEC.z;

        int maxVerts = (activeFootprintCount * MAX_QUADS_PER_FOOTPRINT + activeSparkleCount) * VERTICES_PER_QUAD;
        int vertexBytes = Math.max(1, maxVerts) * DefaultVertexFormat.PARTICLE.getVertexSize();

        GpuBuffer uploadedVertexBuffer;
        GpuBuffer indexBuffer;
        var indexType = com.mojang.blaze3d.IndexType.SHORT;
        int indexCount;

        try (ByteBufferBuilder bbb = ByteBufferBuilder.exactlySized(vertexBytes)) {
            BufferBuilder builder = new BufferBuilder(bbb, PrimitiveTopology.QUADS, DefaultVertexFormat.PARTICLE);
            emitAllGeometry(builder, durationSec);

            try (MeshData mesh = builder.build()) {
                if (mesh == null) {
                    return;
                }
                indexCount = mesh.drawState().indexCount();
                if (indexCount <= 0) {
                    return;
                }
                uploadedVertexBuffer = uploadVertexBuffer(mesh.vertexBuffer());
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                indexBuffer = autoIndices.getBuffer(indexCount);
                indexType = autoIndices.type();
            }
        }

        GpuBufferSlice transformSlice = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Wish Cat Paw Trails",
                target.getColorTextureView(),
                Optional.empty(),
                target.getDepthTextureView(),
                OptionalDouble.empty()
        )) {
            pass.setPipeline(RenderPipelines.WEATHER_NO_DEPTH_WRITE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transformSlice);
            pass.bindTexture(
                    "Sampler2",
                    mc.gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            );
            pass.bindTexture("Sampler0", atlasTexture.getTextureView(), atlasTexture.getSampler());
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.setVertexBuffer(0, uploadedVertexBuffer.slice());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
    }

    private static GpuBuffer uploadVertexBuffer(ByteBuffer data) {
        var device = RenderSystem.getDevice();
        if (vertexBuffer == null || vertexBuffer.isClosed() || vertexBuffer.size() < data.remaining()) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = device.createBuffer(() -> "Wish Cat Paw Vertex Buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.remaining());
        }
        device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(), data);
        return vertexBuffer;
    }

    private static void trackPlayerStep(ClientLevel level, Player player, long nowMs) {
        int slot = findOrAllocatePlayerSlot(player.getId(), player.getX(), player.getY(), player.getZ());
        TR_LAST_TICK_SEEN[slot] = tickCounter;

        if (!player.onGround() || player.isSpectator() || player.isInvisible()
                || player.isPassenger() || player.isSleeping()) {
            TR_LAST_X[slot] = player.getX();
            TR_LAST_Y[slot] = player.getY();
            TR_LAST_Z[slot] = player.getZ();
            return;
        }

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double dx = px - TR_LAST_X[slot];
        double dz = pz - TR_LAST_Z[slot];
        double distSq = dx * dx + dz * dz;

        if (distSq > TELEPORT_DIST_SQ || Math.abs(py - TR_LAST_Y[slot]) > TELEPORT_MAX_DY) {
            TR_LAST_X[slot] = px;
            TR_LAST_Y[slot] = py;
            TR_LAST_Z[slot] = pz;
            return;
        }

        if (distSq < MIN_STEP_DIST_SQ) {
            return;
        }

        double dist = Math.sqrt(distSq);
        double dirX = dx / dist;
        double dirZ = dz / dist;
        double perpX = -dirZ;
        double perpZ = dirX;

        boolean isLeft = TR_NEXT_LEFT[slot];
        TR_NEXT_LEFT[slot] = !isLeft;

        float lateral = CatPawTrailMath.computeStepLateralOffset(isLeft);
        double spawnX = px + perpX * lateral;
        double spawnZ = pz + perpZ * lateral;
        double groundY = resolveGroundSurfaceY(level, spawnX, py, spawnZ) + CatPawTrailMath.GROUND_Y_OFFSET;

        float sinSign = isLeft ? -SIN_7_DEG : SIN_7_DEG;
        float fwdX = (float) (dirX * COS_7_DEG - dirZ * sinSign);
        float fwdZ = (float) (dirX * sinSign + dirZ * COS_7_DEG);
        float rightX = -fwdZ;
        float rightZ = fwdX;

        spawnFootprint((float) spawnX, (float) groundY, (float) spawnZ, fwdX, fwdZ, rightX, rightZ, nowMs);
        spawnSparkles((float) spawnX, (float) (groundY + 0.05), (float) spawnZ, nowMs);

        TR_LAST_X[slot] = px;
        TR_LAST_Y[slot] = py;
        TR_LAST_Z[slot] = pz;
    }

    private static int findOrAllocatePlayerSlot(int entityId, double x, double y, double z) {
        int emptyIdx = -1;
        int oldestIdx = 0;
        long oldestTick = Long.MAX_VALUE;

        for (int i = 0; i < MAX_TRACKED_PLAYERS; i++) {
            if (TR_ENTITY_ID[i] == entityId) {
                return i;
            }
            if (TR_ENTITY_ID[i] == Integer.MIN_VALUE) {
                if (emptyIdx < 0) {
                    emptyIdx = i;
                }
            } else if (TR_LAST_TICK_SEEN[i] < oldestTick) {
                oldestTick = TR_LAST_TICK_SEEN[i];
                oldestIdx = i;
            }
        }

        int target = emptyIdx >= 0 ? emptyIdx : oldestIdx;
        TR_ENTITY_ID[target] = entityId;
        TR_LAST_X[target] = x;
        TR_LAST_Y[target] = y;
        TR_LAST_Z[target] = z;
        TR_NEXT_LEFT[target] = false;
        TR_LAST_TICK_SEEN[target] = tickCounter;
        return target;
    }

    private static double resolveGroundSurfaceY(ClientLevel level, double x, double footY, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int byTop = Mth.floor(footY + 0.05);
        double localX = x - bx;
        double localZ = z - bz;

        double found = sampleBlockCollisionTop(level, bx, byTop, bz, localX, localZ, footY);
        if (!Double.isNaN(found)) {
            return found;
        }
        found = sampleBlockCollisionTop(level, bx, byTop - 1, bz, localX, localZ, footY);
        return Double.isNaN(found) ? footY : found;
    }

    private static double sampleBlockCollisionTop(
            ClientLevel level,
            int bx,
            int by,
            int bz,
            double localX,
            double localZ,
            double footY
    ) {
        SCRATCH_POS.set(bx, by, bz);
        BlockState state = level.getBlockState(SCRATCH_POS);
        VoxelShape shape = state.getCollisionShape(level, SCRATCH_POS);
        if (shape.isEmpty()) {
            return Double.NaN;
        }
        double maxH = shape.max(Direction.Axis.Y, localX, localZ);
        if (maxH >= 0.0 && Double.isFinite(maxH)) {
            double top = by + maxH;
            if (Math.abs(top - footY) <= 0.65) {
                return top;
            }
        }
        return Double.NaN;
    }

    private static void spawnFootprint(
            float x,
            float y,
            float z,
            float fwdX,
            float fwdZ,
            float rightX,
            float rightZ,
            long nowMs
    ) {
        int idx = fpCursor;
        fpCursor = (fpCursor + 1) % MAX_FOOTPRINTS;
        if (!FP_ACTIVE[idx]) {
            activeFootprintCount++;
        }
        FP_X[idx] = x;
        FP_Y[idx] = y;
        FP_Z[idx] = z;
        FP_FWD_X[idx] = fwdX;
        FP_FWD_Z[idx] = fwdZ;
        FP_RIGHT_X[idx] = rightX;
        FP_RIGHT_Z[idx] = rightZ;
        FP_SPAWN_MS[idx] = nowMs;
        FP_ACTIVE[idx] = true;
    }

    private static void spawnSparkles(float x, float y, float z, long nowMs) {
        int count = 2 + RNG.nextInt(2);
        for (int i = 0; i < count; i++) {
            int idx = spCursor;
            spCursor = (spCursor + 1) % MAX_SPARKLES;
            if (!SP_ACTIVE[idx]) {
                activeSparkleCount++;
            }
            boolean isHeart = (i == 0 && RNG.nextFloat() < 0.50f);
            SP_X[idx] = x + (RNG.nextFloat() - 0.5f) * 0.36f;
            SP_Y[idx] = y + RNG.nextFloat() * 0.08f;
            SP_Z[idx] = z + (RNG.nextFloat() - 0.5f) * 0.36f;
            SP_VX[idx] = (RNG.nextFloat() - 0.5f) * 0.14f;
            SP_VY[idx] = 0.28f + RNG.nextFloat() * 0.20f;
            SP_VZ[idx] = (RNG.nextFloat() - 0.5f) * 0.14f;
            SP_SPAWN_MS[idx] = nowMs;
            SP_LIFE_SEC[idx] = 0.75f + RNG.nextFloat() * 0.40f;
            SP_SCALE[idx] = (isHeart ? 0.062f : 0.056f) + RNG.nextFloat() * 0.025f;
            SP_ROT[idx] = isHeart ? (RNG.nextFloat() - 0.5f) * 0.25f : RNG.nextFloat() * ((float) Math.PI * 2.0f);
            SP_SPIN[idx] = isHeart ? 0.0f : (RNG.nextFloat() - 0.5f) * 2.4f;
            SP_IS_HEART[idx] = isHeart;
            SP_ACTIVE[idx] = true;
        }
    }

    private static void sweepExpired(long nowMs, float durationSec) {
        int fpCount = 0;
        for (int i = 0; i < MAX_FOOTPRINTS; i++) {
            if (!FP_ACTIVE[i]) {
                continue;
            }
            float ageSec = (nowMs - FP_SPAWN_MS[i]) * 0.001f;
            if (ageSec < 0.0f || ageSec >= durationSec) {
                FP_ACTIVE[i] = false;
            } else {
                fpCount++;
            }
        }
        activeFootprintCount = fpCount;

        int spCount = 0;
        for (int i = 0; i < MAX_SPARKLES; i++) {
            if (!SP_ACTIVE[i]) {
                continue;
            }
            float ageSec = (nowMs - SP_SPAWN_MS[i]) * 0.001f;
            if (ageSec < 0.0f || ageSec >= SP_LIFE_SEC[i]) {
                SP_ACTIVE[i] = false;
            } else {
                spCount++;
            }
        }
        activeSparkleCount = spCount;
    }

    private static boolean ensureAtlasLoaded(Minecraft mc) {
        if (atlasFailed) {
            return false;
        }
        if (atlasTexture != null) {
            return true;
        }
        NativeImage image = null;
        try {
            image = new NativeImage(NativeImage.Format.RGBA, CatPawTrailMath.ATLAS_SIZE, CatPawTrailMath.ATLAS_SIZE, false);
            for (int y = 0; y < CatPawTrailMath.ATLAS_SIZE; y++) {
                for (int x = 0; x < CatPawTrailMath.ATLAS_SIZE; x++) {
                    image.setPixelABGR(x, y, CatPawTrailMath.sampleAtlasPixelAbgr(x, y));
                }
            }
            atlasTexture = new CatPawAtlasTexture(image);
            image = null;
            mc.getTextureManager().register(ATLAS_ID, atlasTexture);
            return true;
        } catch (Exception e) {
            if (image != null) {
                image.close();
            }
            atlasFailed = true;
            LOGGER.error("[Wish] Failed to generate procedural cat paw atlas", e);
            return false;
        }
    }

    private static void emitAllGeometry(BufferBuilder builder, float durationSec) {
        int customRgb = ColorUtils.parseHexRgb(ModConfig.INSTANCE.catPawTrailColor, CatPawTrailMath.DEFAULT_COLOR_RGB);
        int glowRgb = CatPawTrailMath.resolveGlowRgb(customRgb);
        int bodyRgb = CatPawTrailMath.resolveBodyRgb(customRgb);
        int coreRgb = CatPawTrailMath.resolveCoreRgb(customRgb);
        float configScale = clampScale(ModConfig.INSTANCE.catPawTrailScale);

        for (int offset = 0; offset < MAX_FOOTPRINTS; offset++) {
            int i = (fpCursor + offset) % MAX_FOOTPRINTS;
            if (!FP_ACTIVE[i]) {
                continue;
            }
            float ageSec = (frameNowMs - FP_SPAWN_MS[i]) * 0.001f;
            if (ageSec < 0.0f || ageSec >= durationSec) {
                continue;
            }

            float rx = (float) (FP_X[i] - frameCamX);
            float ry = (float) (FP_Y[i] - frameCamY);
            float rz = (float) (FP_Z[i] - frameCamZ);
            if (rx * rx + ry * ry + rz * rz > RENDER_FOOTPRINT_MAX_DIST_SQ) {
                continue;
            }

            emitFootprintLayers(builder, i, rx, ry, rz, ageSec, durationSec, configScale, glowRgb, bodyRgb, coreRgb);
        }

        for (int offset = 0; offset < MAX_SPARKLES; offset++) {
            int i = (spCursor + offset) % MAX_SPARKLES;
            if (!SP_ACTIVE[i]) {
                continue;
            }
            float ageSec = (frameNowMs - SP_SPAWN_MS[i]) * 0.001f;
            float lifeSec = SP_LIFE_SEC[i];
            if (ageSec < 0.0f || ageSec >= lifeSec) {
                continue;
            }

            float rx = (float) ((SP_X[i] + SP_VX[i] * ageSec) - frameCamX);
            float ry = (float) ((SP_Y[i] + SP_VY[i] * ageSec) - frameCamY);
            float rz = (float) ((SP_Z[i] + SP_VZ[i] * ageSec) - frameCamZ);
            if (rx * rx + ry * ry + rz * rz > RENDER_SPARKLE_MAX_DIST_SQ) {
                continue;
            }

            emitSparkleQuad(builder, i, rx, ry, rz, ageSec, lifeSec, configScale, bodyRgb, coreRgb);
        }
    }

    private static void emitFootprintLayers(
            BufferBuilder builder,
            int idx,
            float rx,
            float ry,
            float rz,
            float ageSec,
            float durationSec,
            float configScale,
            int glowRgb,
            int bodyRgb,
            int coreRgb
    ) {
        float alpha = CatPawTrailMath.computeFootprintAlpha(ageSec, durationSec);
        if (alpha <= 0.005f) {
            return;
        }
        float bounce = CatPawTrailMath.computeBounceScale(ageSec, durationSec);
        float baseHalfSize = 0.30f * configScale * bounce;

        float fwdX = FP_FWD_X[idx];
        float fwdZ = FP_FWD_Z[idx];
        float rightX = FP_RIGHT_X[idx];
        float rightZ = FP_RIGHT_Z[idx];

        if (ageSec < RIPPLE_LIFE_SEC) {
            float rt = ageSec / RIPPLE_LIFE_SEC;
            float rippleRadius = (0.19f + rt * 0.26f) * configScale;
            float rippleAlpha = clamp01((1.0f - rt) * 0.75f);
            emitHorizontalQuad(
                    builder, rx, ry + 0.0002f, rz,
                    fwdX, fwdZ, rightX, rightZ, rippleRadius,
                    0.50f + UV_PAD, 0.50f + UV_PAD, 0.75f - UV_PAD, 0.75f - UV_PAD, packArgb(bodyRgb, rippleAlpha)
            );
        }

        float glowAlpha = clamp01(alpha * 0.72f * CatPawTrailMath.FIXED_GLOW_MULT);
        float glowHalfSize = baseHalfSize * (1.0f + 0.14f * CatPawTrailMath.FIXED_GLOW_MULT);
        emitHorizontalQuad(
                builder, rx, ry, rz,
                fwdX, fwdZ, rightX, rightZ, glowHalfSize,
                0.00f + UV_PAD, 0.00f + UV_PAD, 0.50f - UV_PAD, 0.50f - UV_PAD, packArgb(glowRgb, glowAlpha)
        );

        emitHorizontalQuad(
                builder, rx, ry + 0.0016f, rz,
                fwdX, fwdZ, rightX, rightZ, baseHalfSize,
                0.50f + UV_PAD, 0.00f + UV_PAD, 1.00f - UV_PAD, 0.50f - UV_PAD, packArgb(bodyRgb, alpha * 0.98f)
        );

        emitHorizontalQuad(
                builder, rx, ry + 0.0024f, rz,
                fwdX, fwdZ, rightX, rightZ, baseHalfSize,
                0.00f + UV_PAD, 0.50f + UV_PAD, 0.50f - UV_PAD, 1.00f - UV_PAD, packArgb(coreRgb, alpha)
        );
    }

    private static void emitSparkleQuad(
            BufferBuilder builder,
            int idx,
            float rx,
            float ry,
            float rz,
            float ageSec,
            float lifeSec,
            float configScale,
            int bodyRgb,
            int coreRgb
    ) {
        float t = ageSec / lifeSec;
        float alpha = clamp01((float) Math.sin(t * Math.PI));
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        if (alphaByte <= 4) {
            return;
        }

        boolean isHeart = SP_IS_HEART[idx];
        int rgb = isHeart ? bodyRgb : coreRgb;
        int argb = (alphaByte << 24) | (rgb & 0x00FFFFFF);

        float halfS = SP_SCALE[idx] * configScale;
        float angle = SP_ROT[idx] + SP_SPIN[idx] * ageSec;
        float cosA = Mth.cos(angle);
        float sinA = Mth.sin(angle);

        float rvx = (frameCamRightX * cosA + frameCamUpX * sinA) * halfS;
        float rvy = (frameCamRightY * cosA + frameCamUpY * sinA) * halfS;
        float rvz = (frameCamRightZ * cosA + frameCamUpZ * sinA) * halfS;

        float uvx = (frameCamUpX * cosA - frameCamRightX * sinA) * halfS;
        float uvy = (frameCamUpY * cosA - frameCamRightY * sinA) * halfS;
        float uvz = (frameCamUpZ * cosA - frameCamRightZ * sinA) * halfS;

        float u0 = (isHeart ? 0.50f : 0.75f) + UV_PAD;
        float v0 = (isHeart ? 0.75f : 0.50f) + UV_PAD;
        float u1 = (isHeart ? 0.75f : 1.00f) - UV_PAD;
        float v1 = (isHeart ? 1.00f : 0.75f) - UV_PAD;

        emitVertex(builder, rx - rvx + uvx, ry - rvy + uvy, rz - rvz + uvz, u0, v0, argb);
        emitVertex(builder, rx - rvx - uvx, ry - rvy - uvy, rz - rvz - uvz, u0, v1, argb);
        emitVertex(builder, rx + rvx - uvx, ry + rvy - uvy, rz + rvz - uvz, u1, v1, argb);
        emitVertex(builder, rx + rvx + uvx, ry + rvy + uvy, rz + rvz + uvz, u1, v0, argb);
    }

    private static void emitHorizontalQuad(
            BufferBuilder builder,
            float cx,
            float cy,
            float cz,
            float fwdX,
            float fwdZ,
            float rightX,
            float rightZ,
            float halfSize,
            float u0,
            float v0,
            float u1,
            float v1,
            int argb
    ) {
        if ((argb >>> 24) <= 4) {
            return;
        }
        float fx = fwdX * halfSize;
        float fz = fwdZ * halfSize;
        float rx = rightX * halfSize;
        float rz = rightZ * halfSize;

        emitVertex(builder, cx - rx + fx, cy, cz - rz + fz, u0, v0, argb);
        emitVertex(builder, cx - rx - fx, cy, cz - rz - fz, u0, v1, argb);
        emitVertex(builder, cx + rx - fx, cy, cz + rz - fz, u1, v1, argb);
        emitVertex(builder, cx + rx + fx, cy, cz + rz + fz, u1, v0, argb);
    }

    private static void emitVertex(
            BufferBuilder builder,
            float x,
            float y,
            float z,
            float u,
            float v,
            int argb
    ) {
        builder.addVertex(x, y, z)
                .setUv(u, v)
                .setColor(argb)
                .setLight(LightCoordsUtil.FULL_BRIGHT);
    }

    private static int packArgb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(clamp01(alpha) * 255.0f)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static float clampScale(float value) {
        if (Float.isNaN(value)) {
            return CatPawTrailMath.FIXED_SCALE;
        }
        return Math.max(0.5f, Math.min(2.0f, value));
    }

    private static float clampDuration(float value) {
        if (Float.isNaN(value)) {
            return 3.0f;
        }
        return Math.max(1.0f, Math.min(6.0f, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static final class CatPawAtlasTexture extends DynamicTexture {
        CatPawAtlasTexture(NativeImage image) {
            super(() -> "Wish Cat Paw Trail Atlas", image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
}
