package cpw.mods.fml.common.registry;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ASMDataTable.ASMData;

public enum ItemStackHolderInjector
{
    INSTANCE;

    private List<ItemStackHolderRef> itemStackHolders = Lists.newArrayList();

    public void inject() {
        for (ItemStackHolderRef ishr: itemStackHolders) {
            ishr.apply();
        }
    }

    public void findHolders(ASMDataTable table) {
        Set<ASMData> allItemStackHolders = table.getAll(GameRegistry.ItemStackHolder.class.getName());
        Map<String, Class<?>> classCache = Maps.newHashMap();
        for (ASMData data : allItemStackHolders)
        {
            String className = data.getClassName();
            String annotationTarget = data.getObjectName();
            String value = (String) data.getAnnotationInfo().get("value");
            int meta = data.getAnnotationInfo().containsKey("meta") ? (Integer) data.getAnnotationInfo().get("meta") : 0;
            String nbt = data.getAnnotationInfo().containsKey("nbt") ? (String) data.getAnnotationInfo().get("nbt") : "";
            addHolder(classCache, className, annotationTarget, value, meta, nbt);
        }
    }

    private void addHolder(Map<String, Class<?>> classCache, String className, String annotationTarget, String value, Integer meta, String nbt)
    {
        Class<?> clazz;
        if (classCache.containsKey(className))
        {
            clazz = classCache.get(className);
        }
        else
        {
            try
            {
                clazz = Class.forName(className, true, getClass().getClassLoader());
                classCache.put(className, clazz);
            }
            catch (Exception ex)
            {
                // unpossible?
                throw Throwables.propagate(ex);
            }
        }
        try
        {
            Field f = clazz.getField(annotationTarget);
            itemStackHolders.add(new ItemStackHolderRef(f, value, meta, nbt));
        }
        catch (Exception ex)
        {
            // unpossible?
            throw Throwables.propagate(ex);
        }
    }
}