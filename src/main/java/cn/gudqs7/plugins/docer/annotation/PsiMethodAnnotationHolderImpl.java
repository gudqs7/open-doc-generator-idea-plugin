package cn.gudqs7.plugins.docer.annotation;

import cn.gudqs7.plugins.docer.constant.CommentConst;
import cn.gudqs7.plugins.docer.constant.CommentTag;
import cn.gudqs7.plugins.docer.pojo.annotation.CommentInfo;
import cn.gudqs7.plugins.docer.pojo.annotation.CommentInfoTag;
import cn.gudqs7.plugins.docer.pojo.annotation.RequestMapping;
import cn.gudqs7.plugins.docer.pojo.annotation.ResponseCodeInfo;
import cn.gudqs7.plugins.docer.savior.base.BaseSavior;
import cn.gudqs7.plugins.docer.util.ActionUtil;
import cn.gudqs7.plugins.util.PsiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * @author wq
 */
public class PsiMethodAnnotationHolderImpl implements AnnotationHolder {

    private PsiMethod psiMethod;

    public PsiMethodAnnotationHolderImpl(PsiMethod psiMethod) {
        this.psiMethod = psiMethod;
    }

    @Override
    public PsiAnnotation getAnnotation(String qname) {
        return psiMethod.getAnnotation(qname);
    }

    @Override
    public CommentInfoTag getCommentInfoByComment() {
        CommentInfoTag apiModelPropertyTag = new CommentInfoTag();
        for (PsiElement child : psiMethod.getChildren()) {
            if (child instanceof PsiComment) {
                PsiComment psiComment = (PsiComment) child;
                String text = psiComment.getText();
                if (text.startsWith("/**") && text.endsWith("*/")) {
                    String[] lines = text.replaceAll("\r", "").split("\n");
                    for (String line : lines) {
                        if (line.contains("/**") || line.contains("*/")) {
                            continue;
                        }
                        line = line.replaceAll("\\*", "").trim();
                        if (StringUtils.isBlank(line)) {
                            continue;
                        }
                        if (line.contains("@") || line.contains("#")) {
                            if (line.startsWith("@code") || line.startsWith("#code")) {
                                // remove @code itself
                                line = line.substring(5).trim();
                                String[] codeInfoArray = line.split(" ");
                                if (codeInfoArray.length > 1) {
                                    String code = codeInfoArray[0];
                                    String message = codeInfoArray[1];
                                    String reason = "";
                                    if (codeInfoArray.length > 2) {
                                        reason = codeInfoArray[2];
                                    }
                                    ResponseCodeInfo codeInfo = new ResponseCodeInfo(code, message, reason);
                                    apiModelPropertyTag.getResponseCodeInfoList().add(codeInfo);
                                }
                                continue;
                            }

                            String[] tagValArray = line.split(" ");
                            String tag = "";
                            String tagVal = null;
                            if (tagValArray.length > 0) {
                                tag = tagValArray[0].trim();
                            }
                            if (tagValArray.length > 1) {
                                tagVal = line.substring(tag.length()).trim();
                            }
                            tag = tag.substring(1);
                            switch (tag) {
                                case CommentTag.HIDDEN:
                                    apiModelPropertyTag.setHidden(getBooleanVal(tagVal));
                                    break;
                                case CommentTag.IMPORTANT:
                                    apiModelPropertyTag.setImportant(getBooleanVal(tagVal));
                                    break;
                                case CommentTag.NOTES:
                                    String notes = apiModelPropertyTag.getNotes(null);
                                    if (notes != null) {
                                        apiModelPropertyTag.setNotes(notes + CommentConst.BREAK_LINE + tagVal);
                                    } else {
                                        apiModelPropertyTag.setNotes(tagVal);
                                    }
                                    break;
                                default:
                                    List<String> list = apiModelPropertyTag.getOtherTagMap().computeIfAbsent(tag, k -> new ArrayList<>());
                                    list.add(tagVal);
                                    break;
                            }
                        } else {
                            String oldValue = apiModelPropertyTag.getValue(null);
                            if (oldValue != null) {
                                apiModelPropertyTag.setValue(oldValue + CommentConst.BREAK_LINE + line);
                            } else {
                                apiModelPropertyTag.setValue(line);
                            }
                        }
                    }
                }
                break;
            }
        }
        dealRequestMapping(apiModelPropertyTag);
        return apiModelPropertyTag;
    }

    @Override
    public CommentInfo getCommentInfoByAnnotation() {
        CommentInfo commentInfo = new CommentInfo();
        boolean hasOperationAnnotation = hasAnnotation(QNAME_OF_OPERATION);
        if (hasOperationAnnotation) {
            commentInfo.setHidden(getAnnotationValueByOperation("hidden"));
            String value = getAnnotationValueByOperation("value");
            String notes = getAnnotationValueByOperation("notes");
            if (StringUtils.isNotBlank(value)) {
                value = value.replaceAll("\\n", CommentConst.BREAK_LINE);
            }
            if (StringUtils.isNotBlank(notes)) {
                notes = notes.replaceAll("\\n", CommentConst.BREAK_LINE);
            }
            commentInfo.setValue(value);
            commentInfo.setNotes(notes);
        }
        // 添加 knife4j 的 ApiOperationSupport 注解支持, 主要是 includeParameters 和 ignoreParameters
        // 优先级是有 ignoreParameters 则跳过 includeParameters 字段
        if (hasAnnotation(QNAME_OF_OPERATION_SUPPORT)) {
            List<String> includeParameters = getAnnotationValueByQname(QNAME_OF_OPERATION_SUPPORT, "includeParameters");
            addRequestTagInfo(commentInfo, includeParameters, "onlyRequest");
            List<String> ignoreParameters = getAnnotationValueByQname(QNAME_OF_OPERATION_SUPPORT, "ignoreParameters");
            addRequestTagInfo(commentInfo, ignoreParameters, "hiddenRequest");
        }
        if (hasAnnotation(QNAME_OF_RESPONSES)) {
            // 存在多个 code
            List<PsiAnnotation> psiAnnotationList = getAnnotationValueByQname(QNAME_OF_RESPONSES, "value");
            if (psiAnnotationList != null && psiAnnotationList.size() > 0) {
                for (PsiAnnotation psiAnnotation : psiAnnotationList) {
                    Integer code = BaseSavior.getAnnotationValue(psiAnnotation, "code", null);
                    String message = BaseSavior.getAnnotationValue(psiAnnotation, "message", null);
                    ResponseCodeInfo codeInfo = new ResponseCodeInfo(String.valueOf(code), message, "");
                    commentInfo.getResponseCodeInfoList().add(codeInfo);
                }
            }
        } else if (hasAnnotation(QNAME_OF_RESPONSE)) {
            // 存在单个 code
            Integer code = getAnnotationValueByQname(QNAME_OF_RESPONSE, "code");
            String message = getAnnotationValueByQname(QNAME_OF_RESPONSE, "message");
            ResponseCodeInfo codeInfo = new ResponseCodeInfo(String.valueOf(code), message, "");
            commentInfo.getResponseCodeInfoList().add(codeInfo);
        }
        dealRequestMapping(commentInfo);
        return commentInfo;
    }

    private void addRequestTagInfo(CommentInfo commentInfo, List<String> requestParameters, String tagKey) {
        if (CollectionUtils.isNotEmpty(requestParameters)) {
            Set<String> paramNameSet = new HashSet<>(8);
            PsiParameterList parameterList = psiMethod.getParameterList();
            if (!parameterList.isEmpty()) {
                PsiParameter[] parameters = parameterList.getParameters();
                for (PsiParameter parameter : parameters) {
                    paramNameSet.add(parameter.getName());
                }
            }
            List<String> paramList = new ArrayList<>();
            for (String requestParameter : requestParameters) {
                int indexOfPoint = requestParameter.indexOf(".");
                if (indexOfPoint != -1) {
                    String prefix = requestParameter.substring(0, indexOfPoint);
                    if (paramNameSet.contains(prefix)) {
                        paramList.add(requestParameter.substring(indexOfPoint + 1));
                    } else {
                        paramList.add(requestParameter);
                    }
                }
            }
            String request = String.join(",", paramList);
            List<String> tagList = commentInfo.getOtherTagMap().computeIfAbsent(tagKey, k -> new ArrayList<>());
            tagList.add(request);
        }
    }

    private void dealRequestMapping(CommentInfo commentInfo) {
        boolean hasMappingAnnotation = hasAnyOneAnnotation(QNAME_OF_MAPPING, QNAME_OF_GET_MAPPING, QNAME_OF_POST_MAPPING, QNAME_OF_PUT_MAPPING, QNAME_OF_DELETE_MAPPING);
        if (hasMappingAnnotation) {
            if (hasAnnotation(QNAME_OF_MAPPING)) {
                List<String> path = getAnnotationListValueByQname(QNAME_OF_MAPPING, "value");
                if (CollectionUtils.isEmpty(path)) {
                    path = getAnnotationListValueByQname(QNAME_OF_MAPPING, "path");
                }
                String path0 = "";
                if (CollectionUtils.isNotEmpty(path)) {
                    path0 = path.get(0);
                }
                commentInfo.setUrl(path0);
                String method;
                List<String> methodList = getAnnotationListValueByQname(QNAME_OF_MAPPING, "method");
                if (CollectionUtils.isEmpty(methodList)) {
                    method = "GET/POST";
                } else {
                    method = String.join("/", methodList);
                }
                commentInfo.setMethod(method);
            }
            dealHttpMethod(commentInfo, QNAME_OF_POST_MAPPING, RequestMapping.Method.POST);
            dealHttpMethod(commentInfo, QNAME_OF_GET_MAPPING, RequestMapping.Method.GET);
            dealHttpMethod(commentInfo, QNAME_OF_PUT_MAPPING, RequestMapping.Method.PUT);
            dealHttpMethod(commentInfo, QNAME_OF_DELETE_MAPPING, RequestMapping.Method.DELETE);

            // deal controller RequestMapping
            String controllerUrl = "/";
            PsiAnnotation psiAnnotation = psiMethod.getContainingClass().getAnnotation(QNAME_OF_MAPPING);
            if (psiAnnotation != null) {
                controllerUrl = BaseSavior.getAnnotationValue(psiAnnotation, "value", controllerUrl);
                if (controllerUrl == null) {
                    controllerUrl = BaseSavior.getAnnotationValue(psiAnnotation, "path", controllerUrl);
                }
                if (controllerUrl.startsWith("/")) {
                    controllerUrl = controllerUrl.substring(1);
                }
                if (!controllerUrl.endsWith("/")) {
                    controllerUrl = controllerUrl + "/";
                }
            }
            String hostPrefix = getHostPrefix();
            String nowUrl = commentInfo.getUrl("");
            if (nowUrl.startsWith("/")) {
                nowUrl = nowUrl.substring(1);
            }
            commentInfo.setUrl(hostPrefix + controllerUrl + nowUrl);
            for (PsiElement child : psiMethod.getChildren()) {
                if (child instanceof PsiParameterList) {
                    PsiParameterList parameterList = (PsiParameterList) child;
                    boolean parameterHasRequestBody = false;
                    boolean parameterHasFile = false;
                    if (!parameterList.isEmpty()) {
                        for (PsiParameter parameter : parameterList.getParameters()) {
                            PsiAnnotation annotation = parameter.getAnnotation(QNAME_OF_REQUEST_BODY);
                            if (annotation != null) {
                                parameterHasRequestBody = true;
                            }
                            PsiType psiType = parameter.getType();
                            if (PsiUtil.isPsiTypeFromXxx(psiType, parameter.getProject(), QNAME_OF_MULTIPART_FILE)) {
                                parameterHasFile = true;
                            }
                        }
                    }
                    if (parameterHasRequestBody) {
                        commentInfo.setContentType(RequestMapping.ContentType.APPLICATION_JSON);
                    }
                    if (parameterHasFile) {
                        commentInfo.setContentType(RequestMapping.ContentType.FORM_DATA);
                    }
                    break;
                }
            }
        }
    }

    private String getHostPrefix() {
        String hostPrefix = "http://%s:%s/";
        String ip = ActionUtil.getIp();
        String port = "8080";
        String portByConfigFile = getPortByConfigFile();
        if (StringUtils.isNotBlank(portByConfigFile)) {
            port = portByConfigFile;
        }
        return String.format(hostPrefix, ip, port);
    }

    private String getPortByConfigFile() {
        String nameYml = "application.yml";
        String portByYmlFile = getPortByYamlFile(nameYml);
        if (StringUtils.isNotBlank(portByYmlFile)) {
            return portByYmlFile;
        }
        String nameYaml = "application.yaml";
        String portByYamlFile = getPortByYamlFile(nameYaml);
        if (StringUtils.isNotBlank(portByYamlFile)) {
            return portByYamlFile;
        }
        String portByPropertiesFile = getPortByPropertiesFile();
        if (StringUtils.isNotBlank(portByPropertiesFile)) {
            return portByPropertiesFile;
        }
        return null;
    }

    private String getPortByPropertiesFile() {
        Project project = psiMethod.getProject();
        PsiFile[] filesByName = FilenameIndex.getFilesByName(project, "application.properties", GlobalSearchScope.projectScope(project));
        if (filesByName.length > 0) {
            String backPort = null;
            for (PsiFile psiFile : filesByName) {
                String text = psiFile.getText();
                try {
                    Properties properties = new Properties();
                    VirtualFile virtualFile = psiFile.getVirtualFile();
                    properties.load(virtualFile.getInputStream());
                    String port = properties.getProperty("server.port");
                    if (StringUtils.isNotBlank(port)) {
                        PsiFile containingFile = psiMethod.getContainingFile();
                        String path = containingFile.getVirtualFile().getPath();
                        String configFilePath = virtualFile.getPath();
                        String projectBasePath1 = getProjectBasePath(path);
                        String projectBasePath2 = getProjectBasePath(configFilePath);
                        if (projectBasePath1.equals(projectBasePath2)) {
                            return port;
                        }
                        if (backPort == null) {
                            backPort = port;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return backPort;
        }
        return null;
    }

    private String getPortByYamlFile(String name) {
        Project project = psiMethod.getProject();
        PsiFile[] filesByName = FilenameIndex.getFilesByName(project, name, GlobalSearchScope.projectScope(project));
        if (filesByName.length > 0) {
            String backPort = null;
            for (PsiFile psiFile : filesByName) {
                String text = psiFile.getText();
                try {
                    Yaml yaml = new Yaml();
                    Map<String, Object> map = yaml.load(text);
                    if (map != null && map.size() > 0) {
                        Object serverObj = map.get("server");
                        if (serverObj instanceof Map) {
                            Map server = (Map) serverObj;
                            Object portObj = server.get("port");
                            if (portObj != null) {
                                String port = portObj.toString();
                                PsiFile containingFile = psiMethod.getContainingFile();
                                String path = containingFile.getVirtualFile().getPath();
                                String configFilePath = psiFile.getVirtualFile().getPath();
                                String projectBasePath1 = getProjectBasePath(path);
                                String projectBasePath2 = getProjectBasePath(configFilePath);
                                if (projectBasePath1.equals(projectBasePath2)) {
                                    return port;
                                }
                                if (backPort == null) {
                                    backPort = port;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return backPort;
        }
        return null;
    }

    private String getProjectBasePath(String path) {
        int indexOf = path.indexOf("src/");
        if (indexOf != -1) {
            return path.substring(0, path.indexOf("src/"));
        }
        return "";
    }

    private void dealHttpMethod(CommentInfo commentInfo, String qnameOfXxxMapping, String post) {
        if (hasAnnotation(qnameOfXxxMapping)) {
            List<String> path = getAnnotationListValueByQname(qnameOfXxxMapping, "value");
            if (CollectionUtils.isEmpty(path)) {
                path = getAnnotationListValueByQname(qnameOfXxxMapping, "path");
            }
            String path0 = "";
            if (CollectionUtils.isNotEmpty(path)) {
                path0 = path.get(0);
            }
            commentInfo.setUrl(path0);
            commentInfo.setMethod(post);
        }
    }

    @Override
    public CommentInfo getCommentInfo() {
        CommentInfo commentInfo = new CommentInfo();
        boolean hasMappingAnnotation = hasAnnotation(QNAME_OF_OPERATION);
        CommentInfoTag apiModelPropertyByComment = getCommentInfoByComment();
        if (hasMappingAnnotation) {
            if (apiModelPropertyByComment.isImportant()) {
                commentInfo = apiModelPropertyByComment;
            } else {
                commentInfo = getCommentInfoByAnnotation();
                // 即使使用注解, 附加注释也会生效
                apiModelPropertyByComment.getOtherTagMap().putAll(commentInfo.getOtherTagMap());
                commentInfo.setOtherTagMap(apiModelPropertyByComment.getOtherTagMap());
            }
        } else {
            commentInfo = apiModelPropertyByComment;
        }
        commentInfo.setParent(this);
        return commentInfo;
    }

}