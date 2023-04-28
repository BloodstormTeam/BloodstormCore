package net.minecraft.tileentity;

import com.bloodstorm.core.api.event.block.NotePlayEvent;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.bukkit.Instrument;
import org.bukkit.Note;

public class TileEntityNote extends TileEntity
{
    public byte note;
    public boolean previousRedstoneState;
    private static final String __OBFID = "CL_00000362";

    public void writeToNBT(NBTTagCompound p_145841_1_)
    {
        super.writeToNBT(p_145841_1_);
        p_145841_1_.setByte("note", this.note);
    }

    public void readFromNBT(NBTTagCompound p_145839_1_)
    {
        super.readFromNBT(p_145839_1_);
        this.note = p_145839_1_.getByte("note");

        if (this.note < 0)
        {
            this.note = 0;
        }

        if (this.note > 24)
        {
            this.note = 24;
        }
    }

    public void changePitch()
    {
        byte old = note;
        this.note = (byte)((this.note + 1) % 25);
        if (!net.minecraftforge.common.ForgeHooks.onNoteChange(this, old)) return;
        this.markDirty();
    }

    public void triggerNote(World world, int x, int y, int z)
    {
        if (world.getBlock(x, y + 1, z).getMaterial() == Material.air)
        {
            Material material = world.getBlock(x, y - 1, z).getMaterial();
            byte b0 = 0;

            if (material == Material.rock)
            {
                b0 = 1;
            }

            if (material == Material.sand)
            {
                b0 = 2;
            }

            if (material == Material.glass)
            {
                b0 = 3;
            }

            if (material == Material.wood)
            {
                b0 = 4;
            }

            NotePlayEvent notePlayEvent = new NotePlayEvent(this.worldObj.getBlock(x, y, z), Instrument.getByType(b0), new Note(this.note));
            if (notePlayEvent.post()) {
                this.worldObj.addBlockEvent(x, y, z, Blocks.noteblock, notePlayEvent.getInstrument().getType(), notePlayEvent.getNote().getId());
            }
        }
    }

    // Cauldron start
    @Override
    public boolean canUpdate()
    {
        return false;
    }
    // Cauldron end
}