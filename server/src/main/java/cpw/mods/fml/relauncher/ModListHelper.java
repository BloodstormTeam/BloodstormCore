package cpw.mods.fml.relauncher;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.launchwrapper.Launch;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class ModListHelper {
    public static class JsonModList {
        public String repositoryRoot;
        public List<String> modRef;
        public String parentList;
    }
    private static File mcDirectory;
    private static Set<File> visitedFiles = Sets.newHashSet();
    public static final Map<String,File> additionalMods = Maps.newLinkedHashMap();
    static void parseModList(File minecraftDirectory)
    {
        mcDirectory = minecraftDirectory;
        @SuppressWarnings("unchecked")
        Map<String,String> args = (Map<String, String>) Launch.blackboard.get("launchArgs");
        String listFile = args.get("--modListFile");
        if (listFile != null)
        {
            parseListFile(listFile);
        }
        String extraMods = args.get("--mods");
        if (extraMods != null)
        {
            String[] split = extraMods.split(",");
            for (String modFile : split)
            {
                tryAddFile(modFile, null, modFile);
            }
        }
    }
    private static void parseListFile(String listFile) {
        File f;
        try
        {
            f = new File(mcDirectory, listFile).getCanonicalFile();
        } catch (IOException e2)
        {
            return;
        }
        if (!f.exists())
        {
            return;
        }
        if (visitedFiles.contains(f))
        {
            throw new RuntimeException("Loop detected, impossible to load modlistfile");
        }
        String json;
        try {
            json = Files.asCharSource(f, Charsets.UTF_8).read();
        } catch (IOException e1) {
            return;
        }
        Gson gsonParser = new Gson();
        JsonModList modList;
        try {
            modList = gsonParser.fromJson(json, JsonModList.class);
        } catch (JsonSyntaxException e) {
            return;
        }
        visitedFiles.add(f);
        // We visit parents before children, so the additionalMods list is sorted from parent to child
        if (modList.parentList != null) {
            parseListFile(modList.parentList);
        }
        File repoRoot = new File(modList.repositoryRoot);
        if (!repoRoot.exists()) {
            return;
        }

        for (String s : modList.modRef) {
            StringBuilder fileName = new StringBuilder();
            StringBuilder genericName = new StringBuilder();
            String[] parts = s.split(":");
            fileName.append(parts[0].replace('.', File.separatorChar));
            genericName.append(parts[0]);
            fileName.append(File.separatorChar);
            fileName.append(parts[1]).append(File.separatorChar);
            genericName.append(":").append(parts[1]);
            fileName.append(parts[2]).append(File.separatorChar);
            fileName.append(parts[1]).append('-').append(parts[2]);
            if (parts.length == 4)
            {
                fileName.append('-').append(parts[3]);
                genericName.append(":").append(parts[3]);
            }
            fileName.append(".jar");
            tryAddFile(fileName.toString(), repoRoot, genericName.toString());
        }
    }
    private static void tryAddFile(String modFileName, File repoRoot, String descriptor) {
        File modFile = repoRoot != null ? new File(repoRoot,modFileName) : new File(mcDirectory, modFileName);
        if (modFile.exists()) {
            additionalMods.put(descriptor, modFile);
        }
    }
}