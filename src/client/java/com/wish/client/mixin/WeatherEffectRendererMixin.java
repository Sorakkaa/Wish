package com.wish.client.mixin;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.wish.client.features.visual.WeatherManager;
import com.wish.client.util.IWeatherRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {

    @Unique private static final Identifier WISH_RAIN_64PX = Identifier.fromNamespaceAndPath("wish", "textures/environment/custom_rain.png");
    @Unique private static final Identifier WISH_SNOW_64PX = Identifier.fromNamespaceAndPath("wish", "textures/environment/custom_snow.png");

    @Unique private static final int MAX_POOL = 12000;
    @Unique private static final float NEAR_RADIUS = 22.0f;
    @Unique private static final float FAR_RADIUS = 60.0f;
    @Unique private static final float SIM_HEIGHT_BELOW = 22.0f;
    @Unique private static final float SIM_HEIGHT_ABOVE = 30.0f;
    @Unique private static final int GRID_HALF = 62;
    @Unique private static final int GRID_SIZE = 128; // Power of 2 > (GRID_HALF * 2 + 1 = 125) for collision-free toroidal indexing
    @Unique private static final int GRID_MASK = GRID_SIZE - 1;

    @Shadow @Final private float[] columnSizeX;
    @Shadow @Final private float[] columnSizeZ;

    @Shadow
    protected abstract GpuBuffer uploadVertexBuffer(ByteBuffer buffer);

    // Persistent primitive particle pool (zero GC per frame)
    @Unique private final Random wish$rng = new Random();
    @Unique private boolean wish$poolAllocated = false;
    @Unique private int wish$initializedCount = 0;
    @Unique private int wish$lastSimMode = -1;
    @Unique private long wish$lastStepNs = 0L;
    @Unique private double wish$simClock = 0.0;
    @Unique private double wish$lastCamX = Double.NaN;
    @Unique private double wish$lastCamY = Double.NaN;
    @Unique private double wish$lastCamZ = Double.NaN;

    @Unique private float[] wish$px;
    @Unique private float[] wish$py;
    @Unique private float[] wish$pz;
    @Unique private float[] wish$seedSpeed;
    @Unique private float[] wish$seedScale;
    @Unique private float[] wish$phase;
    @Unique private float[] wish$age;
    @Unique private byte[] wish$variant;

    @Unique private final int[] wish$groundYGrid = new int[GRID_SIZE * GRID_SIZE];
    @Unique private final int[] wish$lightGrid = new int[GRID_SIZE * GRID_SIZE];
    @Unique private final int[] wish$cellWx = new int[GRID_SIZE * GRID_SIZE];
    @Unique private final int[] wish$cellWz = new int[GRID_SIZE * GRID_SIZE];
    @Unique private int wish$refreshRowCursor = 0;
    @Unique private int wish$lastCacheMode = -1;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wish$fixCenterColumnNaN(CallbackInfo ci) {
        Arrays.fill(wish$cellWx, Integer.MIN_VALUE);
        Arrays.fill(wish$cellWz, Integer.MIN_VALUE);
        int centerIndex = 16 * 32 + 16;
        if (columnSizeX != null && columnSizeX.length > centerIndex) {
            columnSizeX[centerIndex] = 1.0f;
        }
        if (columnSizeZ != null && columnSizeZ.length > centerIndex) {
            columnSizeZ[centerIndex] = 0.0f;
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void wish$extractSimulatedWeather(ClientLevel level, float partialTick, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
        if (!WeatherManager.isCustomWeatherActive()) {
            wish$lastStepNs = 0L;
            wish$lastSimMode = -1;
            return;
        }
        ci.cancel();

        IWeatherRenderState ws = (state instanceof IWeatherRenderState s) ? s : null;
        if (ws != null) {
            ws.wish$clearParticles();
        }

        int mode = WeatherManager.getMode();
        if (mode == 0 || ws == null) {
            state.intensity = 0.0f;
            wish$lastSimMode = mode;
            wish$lastStepNs = 0L;
            return;
        }

        state.intensity = WeatherManager.getRainLevel();
        state.radius = (int) FAR_RADIUS;

        wish$ensurePool();

        long nowNs = System.nanoTime();
        float dt = (wish$lastStepNs == 0L) ? 0.016f : Math.min(0.05f, Math.max(0.0f, (nowNs - wish$lastStepNs) * 1e-9f));
        wish$lastStepNs = nowNs;
        if (Minecraft.getInstance().isPaused()) {
            dt = 0.0f;
        }

        WeatherManager.stepWind(dt);
        wish$simClock += dt;

        double camX = cameraPos.x;
        double camY = cameraPos.y;
        double camZ = cameraPos.z;
        int camFloorX = Mth.floor(camX);
        int camFloorY = Mth.floor(camY);
        int camFloorZ = Mth.floor(camZ);

        boolean teleported = Double.isNaN(wish$lastCamX)
            || Mth.square(camX - wish$lastCamX) + Mth.square(camY - wish$lastCamY) + Mth.square(camZ - wish$lastCamZ) > 900.0;
        if (wish$lastSimMode != mode || teleported) {
            wish$initializedCount = 0;
            wish$lastSimMode = mode;
        }
        if (teleported || wish$lastCacheMode != mode) {
            Arrays.fill(wish$cellWx, Integer.MIN_VALUE);
            Arrays.fill(wish$cellWz, Integer.MIN_VALUE);
            wish$lastCacheMode = mode;
        }
        wish$lastCamX = camX;
        wish$lastCamY = camY;
        wish$lastCamZ = camZ;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        boolean isSnow = (mode == 2);

        // Keep immediate surroundings (9x9) fresh every frame and sweep 4 distant rows/frame across the 125x125 grid
        for (int dz = -4; dz <= 4; dz++) {
            int wz = camFloorZ + dz;
            int rowOff = (wz & GRID_MASK) << 7;
            for (int dx = -4; dx <= 4; dx++) {
                int wx = camFloorX + dx;
                wish$updateGridCell(level, mutablePos, rowOff | (wx & GRID_MASK), wx, wz, isSnow, camFloorY);
            }
        }
        int totalRows = GRID_HALF * 2 + 1;
        for (int r = 0; r < 4; r++) {
            int rowIdx = wish$refreshRowCursor;
            wish$refreshRowCursor = (wish$refreshRowCursor + 1) % totalRows;
            int wz = camFloorZ - GRID_HALF + rowIdx;
            int rowOff = (wz & GRID_MASK) << 7;
            for (int wx = camFloorX - GRID_HALF; wx <= camFloorX + GRID_HALF; wx++) {
                wish$updateGridCell(level, mutablePos, rowOff | (wx & GRID_MASK), wx, wz, isSnow, camFloorY);
            }
        }

        int baseTarget = isSnow ? 3800 : 4500;
        int targetCount = Math.max(40, Math.min(MAX_POOL, Math.round(baseTarget * WeatherManager.getCount())));

        if (wish$initializedCount < targetCount) {
            for (int i = wish$initializedCount; i < targetCount; i++) {
                wish$spawnParticle(i, level, mutablePos, camX, camY, camZ, camFloorX, camFloorY, camFloorZ, isSnow, true);
            }
            wish$initializedCount = targetCount;
        }

        ws.wish$setSimMode(mode);
        ws.wish$ensureCapacity(targetCount);

        float windX = WeatherManager.getWindX();
        float windZ = WeatherManager.getWindZ();
        float minY = (float) camY - SIM_HEIGHT_BELOW;
        float maxY = (float) camY + SIM_HEIGHT_ABOVE;

        ParticleStatus particleStatus = Minecraft.getInstance().options.particles().get();
        int maxSplashes = (isSnow || particleStatus == ParticleStatus.MINIMAL || dt <= 0.0f)
            ? 0
            : (particleStatus == ParticleStatus.DECREASED ? 6 : 14);
        int splashesSpawned = 0;

        for (int i = 0; i < targetCount; i++) {
            boolean isOuter = (i % 5) >= 2;
            float zoneRadius = isOuter ? FAR_RADIUS : NEAR_RADIUS;
            float zoneSpan = zoneRadius * 2.0f;

            float sx = wish$seedSpeed[i];
            float ph = wish$phase[i];

            float vx, vy, vz, spinRad;
            if (!isSnow) {
                float speedScale = 20.5f + sx * 7.5f;
                float windCarry = 11.5f + sx * 3.2f;
                vx = windX * windCarry + ((ph - 3.14159f) * 0.16f);
                vy = -speedScale;
                vz = windZ * windCarry + (((sx - 0.5f)) * 0.9f);
                spinRad = 0.0f;
            } else {
                double t = wish$simClock * (1.15 + sx * 0.55) + ph;
                float swirlX = (float) (Math.sin(t) * 0.72 + Math.cos(t * 0.53 + ph * 1.9) * 0.42);
                float swirlZ = (float) (Math.cos(t * 0.91 + 0.7) * 0.72 + Math.sin(t * 0.61 - ph * 1.3) * 0.42);
                float swirlY = (float) (Math.sin(t * 1.45) * 0.22);
                float windCarry = 5.6f + sx * 1.8f;
                vx = windX * windCarry + swirlX;
                vy = -(1.45f + sx * 1.15f) + swirlY;
                vz = windZ * windCarry + swirlZ;
                spinRad = (float) (wish$simClock * (0.65 + (sx - 0.5f) * 1.35) + ph);
            }

            float px = wish$px[i] + vx * dt;
            float py = wish$py[i] + vy * dt;
            float pz = wish$pz[i] + vz * dt;
            float age = wish$age[i] + dt;

            // Toroidal horizontal wrap per zone box for seamless 60-block wind flow
            double relX = px - camX;
            double relZ = pz - camZ;
            boolean wrapped = false;
            if (relX < -zoneRadius) {
                px += zoneSpan * (float) Math.ceil((-zoneRadius - relX) / zoneSpan);
                wrapped = true;
            } else if (relX > zoneRadius) {
                px -= zoneSpan * (float) Math.ceil((relX - zoneRadius) / zoneSpan);
                wrapped = true;
            }
            if (relZ < -zoneRadius) {
                pz += zoneSpan * (float) Math.ceil((-zoneRadius - relZ) / zoneSpan);
                wrapped = true;
            } else if (relZ > zoneRadius) {
                pz -= zoneSpan * (float) Math.ceil((relZ - zoneRadius) / zoneSpan);
                wrapped = true;
            }
            if (wrapped && age < 0.25f) {
                age = 0.25f;
            }

            int gridIdx = wish$lookupOrUpdateCell(level, mutablePos, Mth.floor(px), Mth.floor(pz), isSnow, camFloorX, camFloorY, camFloorZ);
            int groundY = wish$groundYGrid[gridIdx];

            if (py <= groundY + 0.04f || py < minY) {
                if (!isSnow && splashesSpawned < maxSplashes && py <= groundY + 0.08f && groundY > level.getMinY()) {
                    double hDistSq = (px - camX) * (px - camX) + (pz - camZ) * (pz - camZ);
                    if (hDistSq < 484.0 && groundY >= camFloorY - 12 && groundY <= camFloorY + 14) {
                        wish$spawnImpactSplash(level, mutablePos, px, groundY, pz);
                        splashesSpawned++;
                    }
                }
                wish$spawnParticle(i, level, mutablePos, camX, camY, camZ, camFloorX, camFloorY, camFloorZ, isSnow, false);
                continue;
            }
            if (py > maxY) {
                float wrappedY = minY + (py - maxY);
                if (wrappedY > groundY + 0.4f) {
                    py = wrappedY;
                }
            }

            wish$px[i] = px;
            wish$py[i] = py;
            wish$pz[i] = pz;
            wish$age[i] = age;

            float rx = (float) (px - camX);
            float ry = (float) (py - camY);
            float rz = (float) (pz - camZ);
            float horizDist = (float) Math.hypot(rx, rz);
            if (horizDist > zoneRadius) {
                continue;
            }

            float fadeMargin = isOuter ? 10.0f : 4.5f;
            float edgeFade = 1.0f - wish$smoothstep(zoneRadius - fadeMargin, zoneRadius, horizDist);
            float topFade = 1.0f - wish$smoothstep(SIM_HEIGHT_ABOVE - 4.5f, SIM_HEIGHT_ABOVE, ry);
            float bottomFade = wish$smoothstep(-SIM_HEIGHT_BELOW, -SIM_HEIGHT_BELOW + 3.0f, ry);
            float spawnFade = Math.min(1.0f, age * 4.5f);
            float eyeDistSq = rx * rx + ry * ry + rz * rz;
            float eyeFade = wish$smoothstep(0.35f, 1.35f, (float) Math.sqrt(eyeDistSq));
            float groundFade = isSnow ? wish$smoothstep(0.0f, 0.45f, py - groundY) : 1.0f;

            float particleAlpha = spawnFade * edgeFade * topFade * bottomFade * eyeFade * groundFade;
            if (particleAlpha <= 0.02f) {
                continue;
            }

            float speedMag = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float dirX = vx / speedMag;
            float dirY = vy / speedMag;
            float dirZ = vz / speedMag;
            float stretch = isSnow ? 1.0f : Math.min(1.45f, speedMag / 22.0f);

            ws.wish$addParticle(
                rx, ry, rz,
                dirX, dirY, dirZ,
                wish$seedScale[i] * stretch,
                spinRad,
                particleAlpha,
                wish$lightGrid[gridIdx],
                wish$variant[i]
            );
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void wish$renderSimulatedWeather(Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
        if (!WeatherManager.isCustomWeatherActive()) {
            return;
        }
        ci.cancel();

        if (WeatherManager.getMode() == 0 || !(state instanceof IWeatherRenderState ws)) {
            return;
        }

        int count = ws.wish$getParticleCount();
        if (count <= 0) {
            return;
        }

        int simMode = ws.wish$getSimMode();
        if (simMode == 0) {
            return;
        }
        boolean isSnow = (simMode == 2);
        float userSize = WeatherManager.getSize();

        Minecraft mc = Minecraft.getInstance();
        TextureManager texManager = mc.getTextureManager();
        Identifier texId = isSnow ? WISH_SNOW_64PX : WISH_RAIN_64PX;
        AbstractTexture texture = texManager.getTexture(texId);
        GpuSampler weatherSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        RenderTarget target = OutputTarget.WEATHER_TARGET.getRenderTarget();
        RenderPipeline pipeline = mc.gameRenderer.gameRenderState().useShaderTransparency()
            ? RenderPipelines.WEATHER_DEPTH_WRITE
            : RenderPipelines.WEATHER_NO_DEPTH_WRITE;

        Camera mainCamera = mc.gameRenderer.mainCamera();
        Vector3fc leftVec = mainCamera.leftVector();
        Vector3fc upVec = mainCamera.upVector();
        float camRightX = -leftVec.x(), camRightY = -leftVec.y(), camRightZ = -leftVec.z();
        float camUpX = upVec.x(), camUpY = upVec.y(), camUpZ = upVec.z();

        float[] rx = ws.wish$rx();
        float[] ry = ws.wish$ry();
        float[] rz = ws.wish$rz();
        float[] dx = ws.wish$dx();
        float[] dy = ws.wish$dy();
        float[] dz = ws.wish$dz();
        float[] scaleArr = ws.wish$scale();
        float[] spinArr = ws.wish$spin();
        float[] alphaArr = ws.wish$alpha();
        int[] lightArr = ws.wish$light();
        byte[] variantArr = ws.wish$variant();

        float maxBrightnessAlpha = isSnow ? 0.94f : 0.90f;
        int vertexBytes = count * 4 * DefaultVertexFormat.PARTICLE.getVertexSize();

        GpuBuffer vertexBuffer;
        GpuBuffer indexBuffer;
        var indexType = com.mojang.blaze3d.IndexType.SHORT;
        int indexCount;

        try (ByteBufferBuilder bbb = ByteBufferBuilder.exactlySized(vertexBytes)) {
            BufferBuilder builder = new BufferBuilder(bbb, PrimitiveTopology.QUADS, DefaultVertexFormat.PARTICLE);

            if (!isSnow) {
                for (int i = 0; i < count; i++) {
                    float px = rx[i], py = ry[i], pz = rz[i];
                    float vx = dx[i], vy = dy[i], vz = dz[i];

                    // Cross product dir x eyePos makes each rain streak face the camera in full 3D
                    float sx = vy * pz - vz * py;
                    float sy = vz * px - vx * pz;
                    float sz = vx * py - vy * px;
                    float sLenSq = sx * sx + sy * sy + sz * sz;
                    if (sLenSq < 1e-6f) {
                        sx = 1.0f; sy = 0.0f; sz = 0.0f;
                    } else {
                        float inv = 1.0f / (float) Math.sqrt(sLenSq);
                        sx *= inv; sy *= inv; sz *= inv;
                    }

                    float horizDist = (float) Math.hypot(px, pz);
                    float distBoostW = 1.0f + Math.min(0.85f, Math.max(0.0f, horizDist - 14.0f) * 0.018f);
                    float distBoostL = 1.0f + Math.min(0.45f, Math.max(0.0f, horizDist - 18.0f) * 0.010f);
                    float s = scaleArr[i] * userSize;
                    float halfW = 0.044f * s * distBoostW;
                    float halfL = 0.34f * s * distBoostL;

                    float wx = sx * halfW, wy = sy * halfW, wz = sz * halfW;
                    float lx = vx * halfL, ly = vy * halfL, lz = vz * halfL;

                    int var = variantArr[i] & 15;
                    int col = var & 3;
                    int row = (var >>> 2) & 3;
                    float u0 = (col * 64.0f + 2.0f) / 256.0f;
                    float u1 = ((col + 1) * 64.0f - 2.0f) / 256.0f;
                    float v0 = (row * 64.0f + 2.0f) / 256.0f;
                    float v1 = ((row + 1) * 64.0f - 2.0f) / 256.0f;

                    int color = ARGB.white(Math.max(0.0f, Math.min(1.0f, alphaArr[i] * maxBrightnessAlpha)));
                    int light = lightArr[i];

                    builder.addVertex(px - wx - lx, py - wy - ly, pz - wz - lz).setUv(u0, v0).setColor(color).setLight(light);
                    builder.addVertex(px - wx + lx, py - wy + ly, pz - wz + lz).setUv(u0, v1).setColor(color).setLight(light);
                    builder.addVertex(px + wx + lx, py + wy + ly, pz + wz + lz).setUv(u1, v1).setColor(color).setLight(light);
                    builder.addVertex(px + wx - lx, py + wy - ly, pz + wz - lz).setUv(u1, v0).setColor(color).setLight(light);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    float px = rx[i], py = ry[i], pz = rz[i];
                    float spin = spinArr[i];
                    float cosA = Mth.cos(spin);
                    float sinA = Mth.sin(spin);

                    float rvx = camRightX * cosA + camUpX * sinA;
                    float rvy = camRightY * cosA + camUpY * sinA;
                    float rvz = camRightZ * cosA + camUpZ * sinA;

                    float uvx = camUpX * cosA - camRightX * sinA;
                    float uvy = camUpY * cosA - camRightY * sinA;
                    float uvz = camUpZ * cosA - camRightZ * sinA;

                    float horizDist = (float) Math.hypot(px, pz);
                    float distBoostS = 1.0f + Math.min(0.65f, Math.max(0.0f, horizDist - 14.0f) * 0.014f);
                    float halfS = 0.11f * scaleArr[i] * userSize * distBoostS;
                    rvx *= halfS; rvy *= halfS; rvz *= halfS;
                    uvx *= halfS; uvy *= halfS; uvz *= halfS;

                    int var = variantArr[i] & 15;
                    int col = var & 3;
                    int row = (var >>> 2) & 3;
                    float u0 = (col * 64.0f + 2.0f) / 256.0f;
                    float u1 = ((col + 1) * 64.0f - 2.0f) / 256.0f;
                    float v0 = (row * 64.0f + 2.0f) / 256.0f;
                    float v1 = ((row + 1) * 64.0f - 2.0f) / 256.0f;

                    int color = ARGB.white(Math.max(0.0f, Math.min(1.0f, alphaArr[i] * maxBrightnessAlpha)));
                    int light = lightArr[i];

                    builder.addVertex(px - rvx + uvx, py - rvy + uvy, pz - rvz + uvz).setUv(u0, v0).setColor(color).setLight(light);
                    builder.addVertex(px - rvx - uvx, py - rvy - uvy, pz - rvz - uvz).setUv(u0, v1).setColor(color).setLight(light);
                    builder.addVertex(px + rvx - uvx, py + rvy - uvy, pz + rvz - uvz).setUv(u1, v1).setColor(color).setLight(light);
                    builder.addVertex(px + rvx + uvx, py + rvy + uvy, pz + rvz + uvz).setUv(u1, v0).setColor(color).setLight(light);
                }
            }

            try (MeshData mesh = builder.buildOrThrow()) {
                vertexBuffer = this.uploadVertexBuffer(mesh.vertexBuffer());
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                indexCount = mesh.drawState().indexCount();
                indexBuffer = autoIndices.getBuffer(indexCount);
                indexType = autoIndices.type();
            }
        }

        GpuBufferSlice transformSlice = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Wish Weather Simulation",
                target.getColorTextureView(),
                Optional.empty(),
                target.getDepthTextureView(),
                OptionalDouble.empty()
        )) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transformSlice);
            pass.bindTexture(
                "Sampler2",
                mc.gameRenderer.lightmap(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            );
            pass.bindTexture("Sampler0", texture.getTextureView(), weatherSampler);
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.setVertexBuffer(0, vertexBuffer.slice());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
    }

    @Unique
    private void wish$ensurePool() {
        if (wish$poolAllocated) {
            return;
        }
        wish$px = new float[MAX_POOL];
        wish$py = new float[MAX_POOL];
        wish$pz = new float[MAX_POOL];
        wish$seedSpeed = new float[MAX_POOL];
        wish$seedScale = new float[MAX_POOL];
        wish$phase = new float[MAX_POOL];
        wish$age = new float[MAX_POOL];
        wish$variant = new byte[MAX_POOL];
        wish$poolAllocated = true;
    }

    @Unique
    private void wish$spawnParticle(int i, ClientLevel level, BlockPos.MutableBlockPos pos,
                                    double camX, double camY, double camZ,
                                    int camFloorX, int camFloorY, int camFloorZ,
                                    boolean isSnow, boolean initialScatter) {
        float zoneRadius = ((i % 5) >= 2) ? FAR_RADIUS : NEAR_RADIUS;
        float ox = (wish$rng.nextFloat() * 2.0f - 1.0f) * zoneRadius;
        float oz = (wish$rng.nextFloat() * 2.0f - 1.0f) * zoneRadius;
        float px = (float) (camX + ox);
        float pz = (float) (camZ + oz);
        int gY = wish$groundYGrid[wish$lookupOrUpdateCell(level, pos, Mth.floor(px), Mth.floor(pz), isSnow, camFloorX, camFloorY, camFloorZ)];

        float minSpawnY = Math.max(gY + 0.4f, (float) camY - SIM_HEIGHT_BELOW);
        float maxSpawnY = (float) camY + SIM_HEIGHT_ABOVE;
        float py;
        if (initialScatter && minSpawnY < maxSpawnY) {
            py = minSpawnY + wish$rng.nextFloat() * (maxSpawnY - minSpawnY);
        } else {
            py = maxSpawnY - wish$rng.nextFloat() * 4.5f;
            if (py <= gY + 0.4f) {
                py = gY + 0.5f + wish$rng.nextFloat() * 5.0f;
            }
        }

        wish$px[i] = px;
        wish$py[i] = py;
        wish$pz[i] = pz;
        wish$seedSpeed[i] = wish$rng.nextFloat();
        wish$seedScale[i] = 0.72f + wish$rng.nextFloat() * 0.58f;
        wish$phase[i] = wish$rng.nextFloat() * ((float) Math.PI * 2.0f);
        wish$age[i] = initialScatter ? 0.5f + wish$rng.nextFloat() : 0.0f;
        wish$variant[i] = (byte) wish$rng.nextInt(16);
    }

    @Unique
    private void wish$updateGridCell(ClientLevel level, BlockPos.MutableBlockPos pos, int slot, int wx, int wz, boolean isSnow, int camFloorY) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
        int lightPosY = Math.max(surfaceY, camFloorY);
        int light = LightCoordsUtil.getLightCoords(level, pos.set(wx, lightPosY, wz));
        if (isSnow) {
            int bLight = (LightCoordsUtil.block(light) * 3 + 15) / 4;
            int sLight = (LightCoordsUtil.sky(light) * 3 + 15) / 4;
            light = LightCoordsUtil.pack(bLight, sLight);
        }
        wish$groundYGrid[slot] = surfaceY;
        wish$lightGrid[slot] = light;
        wish$cellWx[slot] = wx;
        wish$cellWz[slot] = wz;
    }

    @Unique
    private int wish$lookupOrUpdateCell(ClientLevel level, BlockPos.MutableBlockPos pos, int wx, int wz, boolean isSnow,
                                        int camFloorX, int camFloorY, int camFloorZ) {
        int cwx = Math.max(camFloorX - GRID_HALF, Math.min(camFloorX + GRID_HALF, wx));
        int cwz = Math.max(camFloorZ - GRID_HALF, Math.min(camFloorZ + GRID_HALF, wz));
        int slot = ((cwz & GRID_MASK) << 7) | (cwx & GRID_MASK);
        if (wish$cellWx[slot] != cwx || wish$cellWz[slot] != cwz) {
            wish$updateGridCell(level, pos, slot, cwx, cwz, isSnow, camFloorY);
        }
        return slot;
    }

    @Unique
    private static void wish$spawnImpactSplash(ClientLevel level, BlockPos.MutableBlockPos pos, double x, int groundY, double z) {
        pos.set(Mth.floor(x), groundY - 1, Mth.floor(z));
        BlockState bs = level.getBlockState(pos);
        FluidState fs = level.getFluidState(pos);
        double surfaceH = Math.max(
            bs.getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y, x - pos.getX(), z - pos.getZ()),
            fs.getHeight(level, pos)
        );
        if (surfaceH <= 0.0) {
            return;
        }
        ParticleOptions particle = (fs.is(FluidTags.LAVA) || bs.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(bs))
            ? ParticleTypes.SMOKE
            : ParticleTypes.RAIN;
        level.addParticle(particle, x, pos.getY() + surfaceH + 0.02, z, 0.0, 0.0, 0.0);
    }

    @Unique
    private static float wish$smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
}
