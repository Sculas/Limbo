package com.loohp.limbo.player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.loohp.limbo.Limbo;
import com.loohp.limbo.entity.Entity;
import com.loohp.limbo.location.Location;
import com.loohp.limbo.network.protocol.packets.ClientboundLevelChunkWithLightPacket;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutEntityDestroy;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutEntityMetadata;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutSpawnEntity;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutSpawnEntityLiving;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutUnloadChunk;
import com.loohp.limbo.world.ChunkPosition;
import com.loohp.limbo.world.World;

import net.querz.mca.Chunk;

public class PlayerInteractManager {
	
	private Player player;
	
	private Set<Entity> entities;
	private Map<ChunkPosition, Chunk> currentViewing;
	
	public PlayerInteractManager() {
		this.player = null;
		this.entities = new HashSet<>();
		this.currentViewing = new HashMap<>();
	}
	
	protected void setPlayer(Player player) {
		if (this.player == null) {
			this.player = player;
		} else {
			throw new RuntimeException("Player in PlayerInteractManager cannot be changed once created");
		}
	}
	
	public Player getPlayer() {
		return player;
	}
	
	public void update() throws IOException {
		int viewDistanceChunks = Limbo.getInstance().getServerProperties().getViewDistance();
		int viewDistanceBlocks = viewDistanceChunks << 4;
		Location location = player.getLocation();
		Set<Entity> entitiesInRange = player.getWorld().getEntities().stream().filter(each -> each.getLocation().distanceSquared(location) < viewDistanceBlocks * viewDistanceBlocks).collect(Collectors.toSet());
		for (Entity entity : entitiesInRange) {
			if (!entities.contains(entity)) {
				if (entity.getType().isAlive()) {
					PacketPlayOutSpawnEntityLiving packet = new PacketPlayOutSpawnEntityLiving(entity.getEntityId(), entity.getUniqueId(), entity.getType(), entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch(), entity.getPitch(), (short) 0, (short) 0, (short) 0);
					player.clientConnection.sendPacket(packet);
					
					PacketPlayOutEntityMetadata meta = new PacketPlayOutEntityMetadata(entity);
					player.clientConnection.sendPacket(meta);
				} else {
					PacketPlayOutSpawnEntity packet = new PacketPlayOutSpawnEntity(entity.getEntityId(), entity.getUniqueId(), entity.getType(), entity.getX(), entity.getY(), entity.getZ(), entity.getPitch(), entity.getYaw(), (short) 0, (short) 0, (short) 0);
					player.clientConnection.sendPacket(packet);
					
					PacketPlayOutEntityMetadata meta = new PacketPlayOutEntityMetadata(entity);
					player.clientConnection.sendPacket(meta);
				}
			}
		}
		List<Integer> ids = new ArrayList<>();
		for (Entity entity : entities) {
			if (!entitiesInRange.contains(entity)) {
				ids.add(entity.getEntityId());
			}
		}
		for (int id : ids) {
			PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(id);
			player.clientConnection.sendPacket(packet);
		}
		
		entities = entitiesInRange;
		
		int playerChunkX = (int) location.getX() >> 4;
		int playerChunkZ = (int) location.getZ() >> 4;
		World world = location.getWorld();
		
		Map<ChunkPosition, Chunk> chunksInRange = new HashMap<>();
		
		for (int x = playerChunkX - viewDistanceChunks; x < playerChunkX + viewDistanceChunks; x++) {
			for (int z = playerChunkZ - viewDistanceChunks; z < playerChunkZ + viewDistanceChunks; z++) {
				Chunk chunk = world.getChunkAt(x, z);
				if (chunk != null) {
					chunksInRange.put(new ChunkPosition(world, x, z), chunk);
				} else {
					chunksInRange.put(new ChunkPosition(world, x, z), World.EMPTY_CHUNK);
				}
			}
		}
		
		for (Entry<ChunkPosition, Chunk> entry : currentViewing.entrySet()) {
			ChunkPosition chunkPos = entry.getKey();
			if (!chunksInRange.containsKey(chunkPos)) {
				PacketPlayOutUnloadChunk packet = new PacketPlayOutUnloadChunk(chunkPos.getChunkX(), chunkPos.getChunkZ());
				player.clientConnection.sendPacket(packet);
			}
		}
		
		for (Entry<ChunkPosition, Chunk> entry : chunksInRange.entrySet()) {
			ChunkPosition chunkPos = entry.getKey();
			if (!currentViewing.containsKey(chunkPos)) {
				Chunk chunk = chunkPos.getWorld().getChunkAt(chunkPos.getChunkX(), chunkPos.getChunkZ());
				if (chunk == null) {
					ClientboundLevelChunkWithLightPacket chunkdata = new ClientboundLevelChunkWithLightPacket(chunkPos.getChunkX(), chunkPos.getChunkZ(), entry.getValue(), world.getEnvironment(), true, new ArrayList<>(), new ArrayList<>());
					player.clientConnection.sendPacket(chunkdata);
				} else {
					List<Byte[]> blockChunk = world.getLightEngineBlock().getBlockLightBitMask(chunkPos.getChunkX(), chunkPos.getChunkZ());
					if (blockChunk == null) {
						blockChunk = new ArrayList<>();
					}
					List<Byte[]> skyChunk = null;
					if (world.hasSkyLight()) {
						skyChunk = world.getLightEngineSky().getSkyLightBitMask(chunkPos.getChunkX(), chunkPos.getChunkZ());
					}
					if (skyChunk == null) {
						skyChunk = new ArrayList<>();
					}
					ClientboundLevelChunkWithLightPacket chunkdata = new ClientboundLevelChunkWithLightPacket(chunkPos.getChunkX(), chunkPos.getChunkZ(), chunk, world.getEnvironment(), true, skyChunk, blockChunk);
					player.clientConnection.sendPacket(chunkdata);
				}
			}
		}
		
		currentViewing = chunksInRange;
	}

}
