package net.optifine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;

public class ConnectedParser
{
    private String context = null;
    public static final VillagerProfession[] PROFESSIONS_INVALID = new VillagerProfession[0];

    public ConnectedParser(String context)
    {
        this.context = context;
    }

    public String parseName(String path)
    {
        String str = path;
        int pos = path.lastIndexOf(47);

        if (pos >= 0)
        {
            str = path.substring(pos + 1);
        }

        int pos2 = str.lastIndexOf(46);

        if (pos2 >= 0)
        {
            str = str.substring(0, pos2);
        }

        return str;
    }

    public String parseBasePath(String path)
    {
        int pos = path.lastIndexOf(47);
        return pos < 0 ? "" : path.substring(0, pos);
    }

    public MatchBlock[] parseMatchBlocks(String propMatchBlocks)
    {
        if (propMatchBlocks == null)
        {
            return null;
        }
        else
        {
            ArrayList list = new ArrayList();
            String[] blockStrs = Config.tokenize(propMatchBlocks, " ");

            for (int mbs = 0; mbs < blockStrs.length; ++mbs)
            {
                String blockStr = blockStrs[mbs];
                MatchBlock[] mbs1 = this.parseMatchBlock(blockStr);

                if (mbs1 != null)
                {
                    list.addAll(Arrays.asList(mbs1));
                }
            }

            MatchBlock[] var7 = (MatchBlock[])((MatchBlock[])list.toArray(new MatchBlock[list.size()]));
            return var7;
        }
    }

    public MatchBlock[] parseMatchBlock(String blockStr)
    {
        if (blockStr == null)
        {
            return null;
        }
        else
        {
            blockStr = blockStr.trim();

            if (blockStr.length() <= 0)
            {
                return null;
            }
            else
            {
                String[] parts = Config.tokenize(blockStr, ":");
                String domain = "minecraft";
                boolean blockIndex = false;
                byte var14;

                if (parts.length > 1 && this.isFullBlockName(parts))
                {
                    domain = parts[0];
                    var14 = 1;
                }
                else
                {
                    domain = "minecraft";
                    var14 = 0;
                }

                String blockPart = parts[var14];
                String[] params = (String[])Arrays.copyOfRange(parts, var14 + 1, parts.length);
                Block[] blocks = this.parseBlockPart(domain, blockPart);

                if (blocks == null)
                {
                    return null;
                }
                else
                {
                    MatchBlock[] datas = new MatchBlock[blocks.length];

                    for (int i = 0; i < blocks.length; ++i)
                    {
                        Block block = blocks[i];
                        int blockId = Block.getIdFromBlock(block);
                        int[] metadatas = null;

                        if (params.length > 0)
                        {
                            metadatas = this.parseBlockMetadatas(block, params);

                            if (metadatas == null)
                            {
                                return null;
                            }
                        }

                        MatchBlock bd = new MatchBlock(blockId, metadatas);
                        datas[i] = bd;
                    }

                    return datas;
                }
            }
        }
    }

    public boolean isFullBlockName(String[] parts)
    {
        if (parts.length < 2)
        {
            return false;
        }
        else
        {
            String part1 = parts[1];
            return part1.length() < 1 ? false : (this.startsWithDigit(part1) ? false : !part1.contains("="));
        }
    }

    public boolean startsWithDigit(String str)
    {
        if (str == null)
        {
            return false;
        }
        else if (str.length() < 1)
        {
            return false;
        }
        else
        {
            char ch = str.charAt(0);
            return Character.isDigit(ch);
        }
    }

    public Block[] parseBlockPart(String domain, String blockPart)
    {
        if (this.startsWithDigit(blockPart))
        {
            int[] var8 = this.parseIntList(blockPart);

            if (var8 == null)
            {
                return null;
            }
            else
            {
                Block[] var9 = new Block[var8.length];

                for (int var10 = 0; var10 < var8.length; ++var10)
                {
                    int id = var8[var10];
                    Block block1 = Block.getBlockById(id);

                    if (block1 == null)
                    {
                        return null;
                    }

                    var9[var10] = block1;
                }

                return var9;
            }
        }
        else
        {
            String fullName = domain + ":" + blockPart;
            Block block = Block.getBlockFromName(fullName);

            if (block == null)
            {
                return null;
            }
            else
            {
                return new Block[] {block};
            }
        }
    }

    public int[] parseBlockMetadatas(Block block, String[] params)
    {
        if (params.length == 0)
        {
            return null;
        }
        else
        {
            String param0 = params[0];

            if (this.startsWithDigit(param0))
            {
                return this.parseIntList(param0);
            }
            else
            {
                return null;
            }
        }
    }

    public BiomeGenBase[] parseBiomes(String str)
    {
        if (str == null)
        {
            return null;
        }
        else
        {
            str = str.trim();
            boolean negative = false;

            if (str.startsWith("!"))
            {
                negative = true;
                str = str.substring(1);
            }

            String[] biomeNames = Config.tokenize(str, " ");
            ArrayList list = new ArrayList();

            for (int biomeArr = 0; biomeArr < biomeNames.length; ++biomeArr)
            {
                String biomeName = biomeNames[biomeArr];
                BiomeGenBase biome = this.findBiome(biomeName);
                list.add(biome);
            }

            if (negative)
            {
                ArrayList var8 = new ArrayList(Arrays.asList(BiomeGenBase.getBiomeGenArray()));
                var8.removeAll(list);
                list = var8;
            }

            BiomeGenBase[] var9 = (BiomeGenBase[]) list.toArray(new BiomeGenBase[list.size()]);
            return var9;
        }
    }

    public BiomeGenBase findBiome(String biomeName)
    {
        biomeName = biomeName.toLowerCase();

        if (biomeName.equals("nether"))
        {
            return BiomeGenBase.hell;
        }
        else
        {
            BiomeGenBase[] biomeList = BiomeGenBase.getBiomeGenArray();

            for (int i = 0; i < biomeList.length; ++i)
            {
                BiomeGenBase biome = biomeList[i];

                if (biome != null)
                {
                    String name = biome.biomeName.replace(" ", "").toLowerCase();

                    if (name.equals(biomeName))
                    {
                        return biome;
                    }
                }
            }

            return null;
        }
    }

    public int parseInt(String str)
    {
        if (str == null)
        {
            return -1;
        }
        else
        {
            str = str.trim();
            return Config.parseInt(str, -1);
        }
    }

    public int parseInt(String str, int defVal)
    {
        if (str == null)
        {
            return defVal;
        }
        else
        {
            str = str.trim();
            int num = Config.parseInt(str, -1);

            if (num < 0)
            {
                return defVal;
            }
            else
            {
                return num;
            }
        }
    }

    public int[] parseIntList(String str)
    {
        if (str == null)
        {
            return null;
        }
        else
        {
            ArrayList list = new ArrayList();
            String[] intStrs = Config.tokenize(str, " ,");

            for (int ints = 0; ints < intStrs.length; ++ints)
            {
                String i = intStrs[ints];

                if (i.contains("-"))
                {
                    String[] val = Config.tokenize(i, "-");

                    if (val.length == 2)
                    {
                        int min = Config.parseInt(val[0], -1);
                        int max = Config.parseInt(val[1], -1);

                        if (min >= 0 && max >= 0 && min <= max)
                        {
                            for (int n = min; n <= max; ++n)
                            {
                                list.add(n);
                            }
                        }
                    }
                }
                else
                {
                    int var12 = Config.parseInt(i, -1);

                    if (var12 > 0)
                    {
                        list.add(var12);
                    }
                }
            }

            int[] var10 = new int[list.size()];

            for (int var11 = 0; var11 < var10.length; ++var11)
            {
                var10[var11] = ((Integer)list.get(var11)).intValue();
            }

            return var10;
        }
    }

    public RangeListInt parseRangeListInt(String str)
    {
        if (str == null)
        {
            return null;
        }
        else
        {
            RangeListInt list = new RangeListInt();
            String[] parts = Config.tokenize(str, " ,");

            for (int i = 0; i < parts.length; ++i)
            {
                String part = parts[i];
                RangeInt ri = this.parseRangeInt(part);

                if (ri == null)
                {
                    return null;
                }

                list.addRange(ri);
            }

            return list;
        }
    }

    private RangeInt parseRangeInt(String str)
    {
        if (str == null)
        {
            return null;
        }
        else if (str.indexOf(45) >= 0)
        {
            String[] val1 = Config.tokenize(str, "-");

            if (val1.length != 2)
            {
                return null;
            }
            else
            {
                int min = Config.parseInt(val1[0], -1);
                int max = Config.parseInt(val1[1], -1);

                if (min >= 0 && max >= 0)
                {
                    return new RangeInt(min, max);
                }
                else
                {
                    return null;
                }
            }
        }
        else
        {
            int val = Config.parseInt(str, -1);

            if (val < 0)
            {
                return null;
            }
            else
            {
                return new RangeInt(val, val);
            }
        }
    }

    public boolean parseBoolean(String str)
    {
        if (str == null)
        {
            return false;
        }
        else
        {
            String strLower = str.toLowerCase().trim();
            return strLower.equals("true");
        }
    }

    public Boolean parseBooleanObject(String str)
    {
        if (str == null)
        {
            return null;
        }
        else
        {
            String strLower = str.toLowerCase().trim();

            if (strLower.equals("true"))
            {
                return Boolean.TRUE;
            }
            else if (strLower.equals("false"))
            {
                return Boolean.FALSE;
            }
            else
            {
                return null;
            }
        }
    }

    public static int parseColor(String str, int defVal)
    {
        if (str == null)
        {
            return defVal;
        }
        else
        {
            str = str.trim();

            try
            {
                return Integer.parseInt(str, 16) & 16777215;
            }
            catch (NumberFormatException var3)
            {
                return defVal;
            }
        }
    }

    public NbtTagValue parseNbtTagValue(String path, String value)
    {
        return path != null && value != null ? new NbtTagValue(path, value) : null;
    }

    public VillagerProfession[] parseProfessions(String profStr)
    {
        if (profStr == null)
        {
            return null;
        }
        else
        {
            ArrayList list = new ArrayList();
            String[] tokens = Config.tokenize(profStr, " ");

            for (int arr = 0; arr < tokens.length; ++arr)
            {
                String str = tokens[arr];
                VillagerProfession prof = this.parseProfession(str);

                if (prof == null)
                {
                    return PROFESSIONS_INVALID;
                }

                list.add(prof);
            }

            if (list.isEmpty())
            {
                return null;
            }
            else
            {
                return (VillagerProfession[]) list.toArray(new VillagerProfession[list.size()]);
            }
        }
    }

    private VillagerProfession parseProfession(String str)
    {
        str = str.toLowerCase();
        String[] parts = Config.tokenize(str, ":");

        if (parts.length > 2)
        {
            return null;
        }
        else
        {
            String profStr = parts[0];
            String carStr = null;

            if (parts.length > 1)
            {
                carStr = parts[1];
            }

            int prof = parseProfessionId(profStr);

            if (prof < 0)
            {
                return null;
            }
            else
            {
                int[] cars = null;

                if (carStr != null)
                {
                    cars = parseCareerIds(prof, carStr);

                    if (cars == null)
                    {
                        return null;
                    }
                }

                return new VillagerProfession(prof, cars);
            }
        }
    }

    private static int parseProfessionId(String str)
    {
        int id = Config.parseInt(str, -1);
        return id >= 0 ? id : (str.equals("farmer") ? 0 : (str.equals("librarian") ? 1 : (str.equals("priest") ? 2 : (str.equals("blacksmith") ? 3 : (str.equals("butcher") ? 4 : (str.equals("nitwit") ? 5 : -1))))));
    }

    private static int[] parseCareerIds(int prof, String str)
    {
        HashSet set = new HashSet();
        String[] parts = Config.tokenize(str, ",");
        int i;

        for (int integerArr = 0; integerArr < parts.length; ++integerArr)
        {
            String arr = parts[integerArr];
            i = parseCareerId(prof, arr);

            if (i < 0)
            {
                return null;
            }

            set.add(Integer.valueOf(i));
        }

        Integer[] var7 = (Integer[])((Integer[])set.toArray(new Integer[set.size()]));
        int[] var8 = new int[var7.length];

        for (i = 0; i < var8.length; ++i)
        {
            var8[i] = var7[i].intValue();
        }

        return var8;
    }

    private static int parseCareerId(int prof, String str)
    {
        int id = Config.parseInt(str, -1);

        if (id >= 0)
        {
            return id;
        }
        else
        {
            if (prof == 0)
            {
                if (str.equals("farmer"))
                {
                    return 1;
                }

                if (str.equals("fisherman"))
                {
                    return 2;
                }

                if (str.equals("shepherd"))
                {
                    return 3;
                }

                if (str.equals("fletcher"))
                {
                    return 4;
                }
            }

            if (prof == 1)
            {
                if (str.equals("librarian"))
                {
                    return 1;
                }

                if (str.equals("cartographer"))
                {
                    return 2;
                }
            }

            if (prof == 2 && str.equals("cleric"))
            {
                return 1;
            }
            else
            {
                if (prof == 3)
                {
                    if (str.equals("armor"))
                    {
                        return 1;
                    }

                    if (str.equals("weapon"))
                    {
                        return 2;
                    }

                    if (str.equals("tool"))
                    {
                        return 3;
                    }
                }

                if (prof == 4)
                {
                    if (str.equals("butcher"))
                    {
                        return 1;
                    }

                    if (str.equals("leather"))
                    {
                        return 2;
                    }
                }

                return prof == 5 && str.equals("nitwit") ? 1 : -1;
            }
        }
    }
}
