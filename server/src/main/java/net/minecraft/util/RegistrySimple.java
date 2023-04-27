package net.minecraft.util;

import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class RegistrySimple implements IRegistry {
    protected final Map registryObjects = this.createUnderlyingMap();
    private static final String __OBFID = "CL_00001210";

    protected Map createUnderlyingMap()
    {
        return Maps.newHashMap();
    }

    public Object getObject(Object p_82594_1_)
    {
        return this.registryObjects.get(p_82594_1_);
    }

    public void putObject(Object p_82595_1_, Object p_82595_2_)
    {
        this.registryObjects.put(p_82595_1_, p_82595_2_);
    }

    public Set getKeys()
    {
        return Collections.unmodifiableSet(this.registryObjects.keySet());
    }

    public boolean containsKey(Object p_148741_1_)
    {
        return this.registryObjects.containsKey(p_148741_1_);
    }
}