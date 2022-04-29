package cn.gudqs7.plugins.docer.reader.base;

import cn.gudqs7.plugins.docer.pojo.StructureAndCommentInfo;
import cn.gudqs7.plugins.docer.savior.base.BaseSavior;
import cn.gudqs7.plugins.docer.theme.Theme;
import com.intellij.openapi.project.Project;

import java.util.HashMap;
import java.util.Map;

/**
 * @author WQ
 * @date 2022/4/4
 */
public abstract class AbstractReader<T, B> extends BaseSavior implements IStructureAndCommentReader<B> {

    protected Project project;

    public AbstractReader(Theme theme) {
        super(theme);
    }

    @Override
    public B read(StructureAndCommentInfo structureAndCommentInfo) {
        Map<String, Object> data = new HashMap<>(8);
        return read(structureAndCommentInfo, data);
    }

    @Override
    public B read(StructureAndCommentInfo structureAndCommentInfo, Map<String, Object> data) {
        if (structureAndCommentInfo == null) {
            return handleReturnNull();
        }
        project = structureAndCommentInfo.getProject();
        Map<String, Object> parentData = new HashMap<>(8);
        beforeRead(structureAndCommentInfo, data);
        read0(structureAndCommentInfo, data, parentData, false);
        return afterRead(structureAndCommentInfo, data);
    }

    public T read0(StructureAndCommentInfo structureAndCommentInfo, Map<String, Object> data, Map<String, Object> parentData, boolean fromLoop) {
        if (structureAndCommentInfo == null) {
            return null;
        }

        boolean leaf = structureAndCommentInfo.isLeaf();
        if (leaf) {
            if (fromLoop) {
                return readLeaf(structureAndCommentInfo, data, parentData);
            } else {
                Map<String, Object> loopData = new HashMap<>(8);
                beforeLoop(structureAndCommentInfo, loopData, data, parentData);
                T leafData = readLeaf(structureAndCommentInfo, data, parentData);
                inLoop(structureAndCommentInfo, leafData, loopData, data, parentData);
                afterLoop(structureAndCommentInfo, data, parentData, loopData, leafData, true);
                return leafData;
            }
        } else {
            T leafData = readLeaf(structureAndCommentInfo, data, parentData);
            Map<String, StructureAndCommentInfo> children = structureAndCommentInfo.getChildren();
            if (children != null && children.size() > 0) {
                Map<String, Object> loopData = new HashMap<>(8);
                // todo 可考虑将 parentData 重置, 以做到 parentData 总是父节点相关的数据; 目前是, 父节点/父父节点... 数据都可以在里面
                beforeLoop(structureAndCommentInfo, loopData, data, parentData);
                for (Map.Entry<String, StructureAndCommentInfo> entry : children.entrySet()) {
                    StructureAndCommentInfo value = entry.getValue();
                    beforeLoop0(value, structureAndCommentInfo, data, parentData);
                    T leafData0 = read0(value, data, parentData, true);
                    if (leafData0 != null) {
                        inLoop(value, leafData0, loopData, data, parentData);
                    }
                }
                afterLoop(structureAndCommentInfo, data, parentData, loopData, leafData, false);
            }
            return leafData;
        }
    }

    protected <V> V getFromData(Map<String, Object> data, String key) {
        return getFromData(data, key, null);
    }

    protected <V> V getFromData(Map<String, Object> data, String key, V defaultVal) {
        try {
            if (data != null && data.size() > 0) {
                Object val = data.get(key);
                if (val != null) {
                    return (V) val;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取数据失败, key=" + key + " , data=" + data);
        }
        if (defaultVal != null) {
            return defaultVal;
        }
        throw new RuntimeException("数据为空, key=" + key + " , data=" + data);
    }

    /**
     * 初始化数据
     *
     * @param structureAndCommentInfo 结构+注释信息
     * @param data                    主数据
     */
    protected abstract void beforeRead(StructureAndCommentInfo structureAndCommentInfo, Map<String, Object> data);

    /**
     * 拿出结果处理
     *
     * @param structureAndCommentInfo 结构+注释信息
     * @param data                    主数据
     * @return 最终数据
     */
    protected abstract B afterRead(StructureAndCommentInfo structureAndCommentInfo, Map<String, Object> data);

    /**
     * 循环前初始化
     *
     * @param structureAndCommentInfo 结构+注释信息
     * @param data                    主数据
     * @param parentData              变化数据
     * @param loopData                循环数据
     */
    protected abstract void beforeLoop(StructureAndCommentInfo structureAndCommentInfo, Map<String, Object> loopData, Map<String, Object> data, Map<String, Object> parentData);

    /**
     * 循环中初始化
     *
     * @param structureAndCommentInfo       结构+注释信息
     * @param data                          主数据
     * @param parentData                    变化数据
     * @param parentStructureAndCommentInfo 父信息
     */
    protected abstract void beforeLoop0(StructureAndCommentInfo structureAndCommentInfo, StructureAndCommentInfo parentStructureAndCommentInfo, Map<String, Object> data, Map<String, Object> parentData);

    /**
     * 循环实际代码
     *
     * @param structureAndCommentInfo 结构+注释信息
     * @param data                    主数据
     * @param parentData              变化数据
     * @param leafData                遍历中的节点数据
     * @param loopData                循环数据
     */
    protected abstract void inLoop(StructureAndCommentInfo structureAndCommentInfo, T leafData, Map<String, Object> loopData, Map<String, Object> data, Map<String, Object> parentData);

    /**
     * 循环后处理
     *
     * @param structureAndCommentInfo 结构+注释信息
     * @param data                    主数据
     * @param parentData              变化数据
     * @param loopData                循环数据
     * @param leafData                当前节点数据
     * @param leaf                    是否子节点
     */
    protected abstract void afterLoop(StructureAndCommentInfo structureAndCommentInfo, Map<String, Object> data, Map<String, Object> parentData, Map<String, Object> loopData, T leafData, boolean leaf);

    /**
     * 组装节点数据
     *
     * @param structureAndCommentInfo 结构+注释信息
     * @param data                    主数据
     * @param parentData              变化数据
     * @return 节点数据
     */
    protected abstract T readLeaf(StructureAndCommentInfo structureAndCommentInfo, Map<String, Object> data, Map<String, Object> parentData);

    /**
     * 处理 structureAndCommentInfo 为空的情况
     *
     * @return 返回数据
     */
    protected abstract B handleReturnNull();

}