package com.michallves.ships.ship.core;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public abstract class ShipBlockBase {

    private final int blockId;
    private final BlockType blockType;
    private final float mass;
    private final float buoyancy;

    protected ShipBlockBase(int blockId, BlockType blockType, float mass, float buoyancy) {
        this.blockId = blockId;
        this.blockType = blockType;
        this.mass = mass;
        this.buoyancy = buoyancy;
    }

    public final int getBlockId() {
        return blockId;
    }

    public final BlockType getBlockType() {
        return blockType;
    }

    public final float getMass() {
        return mass;
    }

    public final float getBuoyancy() {
        return buoyancy;
    }

    public static final class Registry {
        private final Int2ObjectOpenHashMap<ShipBlockBase> blocks = new Int2ObjectOpenHashMap<>();

        public ShipBlockBase register(ShipBlockBase block) {
            blocks.put(block.getBlockId(), block);
            return block;
        }

        public ShipBlockBase wrapVanilla(int blockId, float mass, float buoyancy) {
            BlockType type = BlockType.getAssetMap().getAsset(blockId);
            return register(new WrappedVanillaBlock(blockId, type, mass, buoyancy));
        }

        public ShipBlockBase get(int blockId) {
            return blocks.get(blockId);
        }

        public boolean isAllowed(int blockId) {
            return blocks.containsKey(blockId);
        }

        public int size() {
            return blocks.size();
        }
    }

    private static final class WrappedVanillaBlock extends ShipBlockBase {
        private WrappedVanillaBlock(int blockId, BlockType blockType, float mass, float buoyancy) {
            super(blockId, blockType, mass, buoyancy);
        }
    }
}
