package com.adrian.myplot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public class PlotGenerator extends ChunkGenerator {

    private final PlotWorld layout;

    public PlotGenerator(int size) {
        this.layout = new PlotWorld("generator", size);
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        int minY = worldInfo.getMinHeight();
        int g = PlotWorld.GROUND_Y;
        int size = layout.size();
        int period = layout.period();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = (chunkX << 4) + x;
                int wz = (chunkZ << 4) + z;

                data.setBlock(x, minY, z, Material.BEDROCK);
                data.setRegion(x, minY + 1, z, x + 1, g - 4, z + 1, Material.STONE);
                data.setRegion(x, g - 4, z, x + 1, g, z + 1, Material.DIRT);

                boolean spawn = layout.inSpawnArea(wx, wz);
                int mx = Math.floorMod(wx - PlotWorld.OFFSET, period);
                int mz = Math.floorMod(wz - PlotWorld.OFFSET, period);
                boolean roadX = mx >= size;
                boolean roadZ = mz >= size;

                Material top;
                if (spawn) {
                    top = ((wx + wz) & 1) == 0 ? Material.QUARTZ_BLOCK : Material.SMOOTH_STONE;
                } else if (!roadX && !roadZ) {
                    top = Material.GRASS_BLOCK;
                } else {
                    boolean sidewalk = (roadX && !roadZ && (mx == size || mx == period - 1))
                            || (roadZ && !roadX && (mz == size || mz == period - 1));
                    top = sidewalk ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE;
                }
                data.setBlock(x, g, z, top);

                // Borde de la parcela (losa sobre el pasto)
                if (!spawn && !roadX && !roadZ
                        && (mx == 0 || mx == size - 1 || mz == 0 || mz == size - 1)) {
                    data.setBlock(x, g + 1, z, PlotWorld.UNCLAIMED_BORDER);
                }

                // Fuente en el centro del spawn
                if (wx >= -3 && wx <= 2 && wz >= -3 && wz <= 2) {
                    boolean ring = wx == -3 || wx == 2 || wz == -3 || wz == 2;
                    boolean pillar = (wx == -1 || wx == 0) && (wz == -1 || wz == 0);
                    if (ring) {
                        data.setBlock(x, g, z, Material.STONE_BRICKS);
                        data.setBlock(x, g + 1, z, Material.STONE_BRICKS);
                    } else if (pillar) {
                        data.setRegion(x, g, z, x + 1, g + 5, z + 1, Material.QUARTZ_PILLAR);
                        data.setBlock(x, g + 5, z, Material.SEA_LANTERN);
                    } else {
                        data.setBlock(x, g, z, Material.WATER);
                    }
                }
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new PlainsProvider();
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return layout.spawnLocation(world);
    }

    private static final class PlainsProvider extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.PLAINS;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(Biome.PLAINS);
        }
    }
}
