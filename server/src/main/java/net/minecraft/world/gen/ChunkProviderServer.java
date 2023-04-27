package net.minecraft.world.gen;

import co.aikar.timings.Timing;
import com.bloodstorm.core.chunk.ChunkMap;
import com.bloodstorm.core.util.ChunkHash;
import cpw.mods.fml.common.registry.GameRegistry;
import io.github.crucible.CrucibleConfigs;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.block.BlockSand;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.ReportedException;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraftforge.cauldron.CauldronHooks;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.chunkio.ChunkIOExecutor;
import org.bukkit.Server;
import org.bukkit.craftbukkit.util.LongHash;
import org.bukkit.craftbukkit.util.LongHashSet;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.List;
import java.util.Random;

public class ChunkProviderServer implements IChunkProvider {
    public Chunk defaultEmptyChunk;
    public IChunkProvider currentChunkProvider;
    public IChunkLoader currentChunkLoader;
    public WorldServer worldObj;
    public IntSet chunksToUnload = new IntLinkedOpenHashSet();
    public ChunkMap chunkMap = new ChunkMap();
    public int initialTick;
    public boolean loadChunkOnProvideRequest = CrucibleConfigs.configs.cauldron_settings_loadChunkOnRequest; // Cauldron - if true, allows mods to force load chunks. to disable, set load-chunk-on-request in cauldron.yml to false

    public ChunkProviderServer(WorldServer p_i1520_1_, IChunkLoader p_i1520_2_, IChunkProvider p_i1520_3_)
    {
        this.initialTick = MinecraftServer.currentTick; // Cauldron keep track of when the loader was created
        this.defaultEmptyChunk = new EmptyChunk(p_i1520_1_, 0, 0);
        this.worldObj = p_i1520_1_;
        this.currentChunkLoader = p_i1520_2_;
        this.currentChunkProvider = p_i1520_3_;
    }

    public boolean chunkExists(int x, int z)
    {
        return chunkMap.contains(x, z);
    }

    public List func_152380_a() // Vanilla compatibility
    {
        return (List) chunkMap.valueCollection();
    }

    public void unloadChunksIfNotNearSpawn(int x, int z)
    {
        // PaperSpigot start - Asynchronous lighting updates
        Chunk chunk = chunkMap.get(x, z);
        if (chunk != null) {
            if (chunk.worldObj.isModded == null) {
                chunk.worldObj.isModded = false;
            }

            if (chunk.worldObj.spigotConfig.useAsyncLighting && (chunk.pendingLightUpdates.get() > 0 || chunk.worldObj.getTotalWorldTime() - chunk.lightUpdateTime < 20)) {
                return;
            }
        }

        if (this.worldObj.provider.canRespawnHere() && DimensionManager.shouldLoadSpawn(this.worldObj.provider.dimensionId)) {
            ChunkCoordinates chunkcoordinates = this.worldObj.getSpawnPoint();
            int k = x * 16 + 8 - chunkcoordinates.posX;
            int l = z * 16 + 8 - chunkcoordinates.posZ;
            short short1 = 128;

            if (k < -short1 || k > short1 || l < -short1 || l > short1) {
                this.chunksToUnload.add(ChunkHash.chunkToKey(x, z));
                if (chunk != null) {
                    chunk.mustSave = true;
                }
            }
        } else {
            this.chunksToUnload.add(ChunkHash.chunkToKey(x, z));
            if (chunk != null) {
                chunk.mustSave = true;
            }
        }
    }

    public void unloadAllChunks()
    {
    	for(Chunk chunk : this.chunkMap.valueCollection())
    	{
    		unloadChunksIfNotNearSpawn(chunk.xPosition, chunk.zPosition);
    	}
    }

    public Chunk getChunkIfLoaded(int x, int z) {
    	return chunkMap.get(x, z);
    }

    public Chunk loadChunk(int x, int z)
    {
        return loadChunk(x, z, null);
    }

    public Chunk loadChunk(int x, int z, Runnable runnable) {
        this.chunksToUnload.remove(ChunkHash.chunkToKey(x, z));
        Chunk chunk = this.chunkMap.get(x, z);
        AnvilChunkLoader loader = null;

        if (this.currentChunkLoader instanceof AnvilChunkLoader) {
            loader = (AnvilChunkLoader) this.currentChunkLoader;
        }

        // We can only use the queue for already generated chunks
        if (chunk == null && loader != null && loader.chunkExists(this.worldObj, x, z)) {
            if (runnable != null) {
                ChunkIOExecutor.queueChunkLoad(this.worldObj, loader, this, x, z, runnable);
                return null;
            } else {
                chunk = ChunkIOExecutor.syncChunkLoad(this.worldObj, loader, this, x, z);
            }
        }
        else if (chunk == null) {
            chunk = this.originalLoadChunk(x, z);
        }

        if (runnable != null) {
            runnable.run();
        }

        return chunk;
    }

    public Chunk originalLoadChunk(int x, int z)
    {
        this.chunksToUnload.remove(ChunkHash.chunkToKey(x, z));
        Chunk chunk = this.chunkMap.get(x, z);
        boolean newChunk = false;

        if (chunk == null) {
            worldObj.timings.syncChunkLoadTimer.startTiming();
            chunk = ForgeChunkManager.fetchDormantChunk(LongHash.toLong(x, z), this.worldObj);
            if (chunk == null) {
                chunk = this.safeLoadChunk(x, z);
            }

            if (chunk == null) {
                if (this.currentChunkProvider == null) {
                    chunk = this.defaultEmptyChunk;
                } else {
                    try {
                        chunk = this.currentChunkProvider.provideChunk(x, z);
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Exception generating new chunk");
                        CrashReportCategory crashreportcategory = crashreport.makeCategory("Chunk to be generated");
                        crashreportcategory.addCrashSection("Location", String.format("%d,%d", new Object[] {Integer.valueOf(x), Integer.valueOf(z)}));
                        crashreportcategory.addCrashSection("Position hash", LongHash.toLong(x, z));
                        crashreportcategory.addCrashSection("Generator", this.currentChunkProvider.makeString());
                        throw new ReportedException(crashreport);
                    }
                }

                newChunk = true; // CraftBukkit
            }

            this.chunkMap.put(x, z, chunk);
            chunk.onChunkLoad();

            Server server = this.worldObj.getServer();

            if (server != null) {
                server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(chunk.bukkitChunk, newChunk));
            }

            // Update neighbor counts
            for (int xX = -2; xX < 3; xX++) {
                for (int zZ = -2; zZ < 3; zZ++) {
                    if (xX == 0 && zZ == 0) {
                        continue;
                    }

                    Chunk neighbor = this.getChunkIfLoaded(chunk.xPosition + xX, chunk.zPosition + zZ);
                    if (neighbor != null) {
                        neighbor.setNeighborLoaded(-xX, -zZ);
                        chunk.setNeighborLoaded(xX, zZ);
                    }
                }
            }
            // CraftBukkit end
            chunk.populateChunk(this, this, x, z);
            worldObj.timings.syncChunkLoadTimer.stopTiming(); // Spigot
        }

        return chunk;
    }

    public Chunk provideChunk(int x, int z)
    {

        // CraftBukkit start
        Chunk chunk = this.chunkMap.get(x, z);
        chunk = chunk == null ? (shouldLoadChunk() ? this.loadChunk(x, z) : this.defaultEmptyChunk) : chunk; // Cauldron handle forge server tick events and load the chunk within 5 seconds of the world being loaded (for chunk loaders)

        if (chunk == this.defaultEmptyChunk) {
            return chunk;
        }
        
        if (chunk == null) {
        	return null;
        }
        
        try {
        	worldObj.isProfilingWorld();
        } catch (Throwable t) {
        	return chunk;
        }
        
        if ((x != chunk.xPosition || z != chunk.zPosition) && !worldObj.isProfilingWorld()) {
            Throwable ex = new Throwable();
            ex.fillInStackTrace();
            ex.printStackTrace();
        }
        chunk.lastAccessedTick = MinecraftServer.getServer().getTickCounter(); // Cauldron
        return chunk;
        // CraftBukkit end
    }

    public Chunk safeLoadChunk(int x, int y) // CraftBukkit - private -> public
    {
        if (this.currentChunkLoader == null) {
            return null;
        } else {
            try {
                Chunk chunk = this.currentChunkLoader.loadChunk(this.worldObj, x, y);

                if (chunk != null) {
                    chunk.lastSaveTime = this.worldObj.getTotalWorldTime();

                    if (this.currentChunkProvider != null) {
                        worldObj.timings.syncChunkLoadStructuresTimer.startTiming(); // Spigot
                        this.currentChunkProvider.recreateStructures(x, y);
                        worldObj.timings.syncChunkLoadStructuresTimer.stopTiming(); // Spigot
                    }
                    chunk.lastAccessedTick = MinecraftServer.getServer().getTickCounter(); // Cauldron
                }

                return chunk;
            } catch (Exception exception) {
                return null;
            }
        }
    }

    public void safeSaveExtraChunkData(Chunk p_73243_1_) {
        if (this.currentChunkLoader != null){
            try {
                this.currentChunkLoader.saveExtraChunkData(this.worldObj, p_73243_1_);
            } catch (Exception ignored) {}
        }
    }

    public void safeSaveChunk(Chunk chunk) {
        if (this.currentChunkLoader != null) {
            try {
                chunk.lastSaveTime = this.worldObj.getTotalWorldTime();
                this.currentChunkLoader.saveChunk(this.worldObj, chunk);
            } catch (Exception ignored) {}
        }
    }

    public void populate(IChunkProvider p_73153_1_, int p_73153_2_, int p_73153_3_)
    {
        Chunk chunk = this.provideChunk(p_73153_2_, p_73153_3_);

        if (!chunk.isTerrainPopulated)
        {
            chunk.func_150809_p();

            if (this.currentChunkProvider != null)
            {
                try (co.aikar.timings.Timing ignored = this.worldObj.timings.syncChunkLoadPopulateTimer.startTiming()) { // Paper //Crucible - Is this right? I think so
                this.currentChunkProvider.populate(p_73153_1_, p_73153_2_, p_73153_3_);
                // CraftBukkit start
                BlockSand.fallInstantly = true;
                Random random = new Random();
                random.setSeed(worldObj.getSeed());
                long xRand = random.nextLong() / 2L * 2L + 1L;
                long zRand = random.nextLong() / 2L * 2L + 1L;
                random.setSeed((long) p_73153_2_ * xRand + (long) p_73153_3_ * zRand ^ worldObj.getSeed());
                org.bukkit.World world = this.worldObj.getWorld();

                if (world != null)
                {
                    this.worldObj.populating = true;

                    try
                    {
                        for (org.bukkit.generator.BlockPopulator populator : world.getPopulators())
                        {
                            populator.populate(world, random, chunk.bukkitChunk);
                        }
                    }
                    finally
                    {
                        this.worldObj.populating = false;
                    }
                }

                BlockSand.fallInstantly = false;
                this.worldObj.getServer().getPluginManager().callEvent(new org.bukkit.event.world.ChunkPopulateEvent(chunk.bukkitChunk));
                // CraftBukkit end
                GameRegistry.generateWorld(p_73153_2_, p_73153_3_, worldObj, currentChunkProvider, p_73153_1_);
                chunk.setChunkModified();
                } // Paper
            }
        }
    }

    public boolean saveChunks(boolean saveExtraData, IProgressUpdate p_73151_2_) {
        try (Timing ignored = worldObj.timings.chunkSaveData.startTiming()) { // Paper - Timings //Crucible - Is this right? I think so
            int i = 0;
            for (Chunk chunk : this.chunkMap.valueCollection()) {
                if (chunk == null) continue;
                if (saveExtraData) this.safeSaveExtraChunkData(chunk);
                if(chunk.needsSaving(saveExtraData)) {
                    this.safeSaveChunk(chunk);
                    chunk.isModified = false;

                    if (i++ == 24 && !saveExtraData) return false;
                }
            }
            return true;
        }
    }

    public void saveExtraData()
    {
        if (this.currentChunkLoader != null)
        {
            this.currentChunkLoader.saveExtraData();
        }
    }

    public boolean unloadQueuedChunks()
    {
        if (!this.worldObj.levelSaving)
        {
            if (!this.chunksToUnload.isEmpty())
            {
                for (ChunkCoordIntPair forcedChunk : this.worldObj.getPersistentChunks().keySet())
                {
                    this.chunksToUnload.remove(ChunkHash.chunkToKey(forcedChunk.chunkXPos, forcedChunk.chunkZPos));
                }
            }

            Server server = this.worldObj.getServer();

            for (int key : this.chunksToUnload) {
                Chunk chunk = this.chunkMap.get(key);

                if (chunk == null)
                {
                    continue;
                }

                if (this.worldObj.playerEntities.size() > 0 && !shouldUnloadChunk(chunk)) continue;

                ChunkUnloadEvent event = new ChunkUnloadEvent(chunk.bukkitChunk);
                server.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    chunk.onChunkUnload();
                    this.safeSaveChunk(chunk);
                    this.safeSaveExtraChunkData(chunk);
                    for (int x = -2; x < 3; x++) {
                        for (int z = -2; z < 3; z++) {
                            if (x == 0 && z == 0) {
                                continue;
                            }

                            Chunk neighbor = this.getChunkIfLoaded(chunk.xPosition + x, chunk.zPosition + z);
                            if (neighbor != null) {
                                neighbor.setNeighborUnloaded(-x, -z);
                                chunk.setNeighborUnloaded(x, z);
                            }
                        }
                    }
                    this.chunkMap.remove(key);
                    ForgeChunkManager.putDormantChunk(key, chunk);
                    if(this.chunkMap.size() == 0 && ForgeChunkManager.getPersistentChunksFor(this.worldObj).size() == 0 && !DimensionManager.shouldLoadSpawn(this.worldObj.provider.dimensionId)){
                        DimensionManager.unloadWorld(this.worldObj.provider.dimensionId);
                        return currentChunkProvider.unloadQueuedChunks();
                    }
                }
            }

            if (this.currentChunkLoader != null)
            {
                this.currentChunkLoader.chunkTick();
            }
        }

        return this.currentChunkProvider.unloadQueuedChunks();
    }

    public boolean canSave()
    {
        return !this.worldObj.levelSaving;
    }

    public String makeString()
    {
        return "ServerChunkCache: " + this.chunkMap.size() + " Drop: " + this.chunksToUnload.size(); // Cauldron
    }

    public List getPossibleCreatures(EnumCreatureType p_73155_1_, int p_73155_2_, int p_73155_3_, int p_73155_4_)
    {
        return this.currentChunkProvider.getPossibleCreatures(p_73155_1_, p_73155_2_, p_73155_3_, p_73155_4_);
    }

    public ChunkPosition func_147416_a(World p_147416_1_, String p_147416_2_, int p_147416_3_, int p_147416_4_, int p_147416_5_)
    {
        return this.currentChunkProvider.func_147416_a(p_147416_1_, p_147416_2_, p_147416_3_, p_147416_4_, p_147416_5_);
    }

    public int getLoadedChunkCount()
    {
        return this.chunkMap.size(); // Cauldron
    }

    public void recreateStructures(int p_82695_1_, int p_82695_2_) {}

    // Cauldron start
    private boolean shouldLoadChunk()
    {
        return this.worldObj.findingSpawnPoint ||
                this.loadChunkOnProvideRequest ||
                (MinecraftServer.callingForgeTick && CrucibleConfigs.configs.cauldron_settings_loadChunkOnForgeTick) ||
                (MinecraftServer.currentTick - initialTick <= 100);
    }

    public long lastAccessed(int x, int z)
    {
        Chunk c = this.chunkMap.get(x, z);
        if(c == null)return 0;
        else return c.lastAccessedTick;
    }

    private boolean shouldUnloadChunk(Chunk chunk)
    {
        if (chunk == null) return false;
        return MinecraftServer.getServer().getTickCounter() - chunk.lastAccessedTick > CrucibleConfigs.configs.cauldron_settings_chunkGCGracePeriod;
    }
}