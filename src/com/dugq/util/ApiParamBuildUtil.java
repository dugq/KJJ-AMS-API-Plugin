package com.dugq.util;

import com.dugq.exception.ErrorException;
import com.dugq.pojo.RequestParam;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Created by dugq on 2019/12/25.
 */
public class ApiParamBuildUtil {






    /**
     * 把对象转换为参数
     * 1、普通对象 返回List<ParamSelectValue> key = parentKey >> paramKey >> 下级对象的key
     * 2、数组，集合 返回数组内对象的列表  key = parentKey >> paramKey >> 下级对象的key
     * 3、基本类型 key = parentKey >> paramKey
     * @param psiField
     * @param project
     * @param parentKey 上级key
     * @param paramKey 本级key
     * @return
     */
    public static List<RequestParam> getParamListFromFiled(PsiField psiField, Project project, String parentKey, String paramKey, List<String> childGenericTypes){
        if(Objects.isNull(psiField)){
            return Collections.emptyList();
        }
        PsiType filedType = psiField.getType();
        if(filedType.getPresentableText().equals("Class<Void>")){
            return Collections.emptyList();
        }
        if(filedType instanceof PsiPrimitiveType || MyPsiTypesUtils.isNormalType(filedType.getPresentableText())){ //基本类型
            RequestParam param = new RequestParam();
            param.setParamKey(getParamKey(parentKey,paramKey));
            param.setParamType(getType(filedType.getPresentableText()));
            param.setParamName(DesUtil.getFiledDesc(psiField.getDocComment()));
            param.setParamValue(DesUtil.getFiledDefaultValue(psiField.getDocComment()));
            param.setParamValueList(DesUtil.getFieldEnumValues(project,psiField.getDocComment()));
            PsiAnnotation notNull = psiField.getAnnotation("javax.validation.constraints.NotNull");
            if(Objects.nonNull(notNull)){
                param.setParamNotNull(0);
            }
            return singletonList(param);
        }else if(MyPsiTypesUtils.dateList.contains(filedType.getPresentableText())){ //时间
            RequestParam param = new RequestParam();
            param.setParamKey(getParamKey(parentKey,paramKey));
            param.setParamType(getType(filedType.getPresentableText()));
            param.setParamName(DesUtil.getFiledDesc(psiField.getDocComment()));
            param.setParamValue(DesUtil.getFiledDefaultValue(psiField.getDocComment()));
            param.setParamValueList(DesUtil.getFieldEnumValues(project,psiField.getDocComment()));
            PsiAnnotation notNull = psiField.getAnnotation("javax.validation.constraints.NotNull");
            if(Objects.nonNull(notNull)){
                param.setParamNotNull(0);
            }
            return singletonList(param);
        }
        else if(filedType.getPresentableText().startsWith("List") || filedType.getPresentableText().startsWith("Set")){ //集合
            String canonicalText = filedType.getCanonicalText();
            String[] split = canonicalText.split("<");
            if(split.length<=0){
                throw new ErrorException(null,psiField,"collection 必须声明泛型！！！！！");
            }
            List<String> collectionChildrenList = new ArrayList<>();
            if(split.length==2 && StringUtils.equals(split[1].split(">")[0], "T")){
                collectionChildrenList = childGenericTypes;
            }else{
                collectionChildrenList.addAll(Arrays.asList(split));
            }
            String childType = collectionChildrenList.get(0);
            childGenericTypes.remove(childType);
            PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(childType.split(">")[0], GlobalSearchScope.allScope(project));
            List<RequestParam> params = getParamListFromClass(psiClassChild,project,getParamKey(parentKey,paramKey),psiField.getName(),collectionChildrenList);
            RequestParam param = new RequestParam();
            param.setParamKey(getParamKey(parentKey,paramKey));
            param.setParamType(getType(filedType.getPresentableText()));
            param.setParamName(DesUtil.getFiledDesc(psiField.getDocComment()));
            params.add(param);
            return params;
        }else if (filedType instanceof PsiArrayType) {  //数组
            PsiType deepType = ((PsiArrayType) filedType).getComponentType();
            String canonicalText = deepType.getCanonicalText();
            PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(canonicalText, GlobalSearchScope.allScope(project));
            List<RequestParam> params = getParamListFromClass(psiClassChild,project,getParamKey(parentKey,paramKey),psiField.getName(),childGenericTypes);
            RequestParam param = new RequestParam();
            param.setParamKey(getParamKey(parentKey,paramKey));
            param.setParamType(getType(deepType.getPresentableText()));
            param.setParamName(DesUtil.getFiledDesc(psiField.getDocComment()));
            param.setParamValue(DesUtil.getFiledDefaultValue(psiField.getDocComment()));
            param.setParamValueList(DesUtil.getFieldEnumValues(project,psiField.getDocComment()));
            params.add(param);
            return params;
        }else if(filedType.getPresentableText().startsWith("Map")){
            throw new ErrorException(null,psiField,"不支持Map类型！！！！");
        }else if(StringUtils.equals(psiField.getType().getPresentableText(),"T")){
            if(CollectionUtils.isEmpty(childGenericTypes)){
                throw new ErrorException(null,psiField,psiField.getName()+"字段泛型未声明");
            }
            String childType = childGenericTypes.get(0).split(">")[0];
            if("java.lang.Void".equals(childType)){
                return Collections.emptyList();
            }
            childGenericTypes.remove(childType);
            PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(childType, GlobalSearchScope.allScope(project));
            List<RequestParam> params = getParamListFromClass(psiClassChild,project,getParamKey(parentKey,paramKey),psiField.getName(),childGenericTypes);
            RequestParam param = new RequestParam();
            param.setParamKey(getParamKey(parentKey,paramKey));
            param.setParamType(getType(psiClassChild.getName()));
            param.setParamName(DesUtil.getFiledDesc(psiField.getDocComment()));
            params.add(param);
            return params;
        }else{  //object
            String canonicalText = filedType.getCanonicalText();
            if (canonicalText.contains("<")){
                String[] types= filedType.getCanonicalText().split("<");
                canonicalText = types[0];
                childGenericTypes = new ArrayList<>();
                for (String type : types){
                    childGenericTypes.add(type.replace(">",""));
                }
                childGenericTypes.remove(canonicalText);
            }
            PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(canonicalText, GlobalSearchScope.allScope(project));
            List<RequestParam> params = getParamListFromClass(psiClassChild,project,getParamKey(parentKey,paramKey),psiField.getName(),childGenericTypes);
            RequestParam param = new RequestParam();
            param.setParamKey(getParamKey(parentKey,paramKey));
            param.setParamType(getType(filedType.getPresentableText()));
            param.setParamName(DesUtil.getFiledDesc(psiField.getDocComment()));
            params.add(param);
            return params;
        }
    }

    public static List<RequestParam> getParamListFromClass(PsiClass psiClassChild, Project project, String parentKey, String paramKey, List<String> childGenericTypes) {
        ArrayList<RequestParam> paramList = new ArrayList<>();
        if(Objects.isNull(psiClassChild)){
            throw new ErrorException(null,null,"缺少泛型指定："+paramKey);
        }
        if(psiClassChild instanceof PsiPrimitiveType || MyPsiTypesUtils.isNormalType(psiClassChild.getName())){ //基本类型
            return paramList;
        }else if(MyPsiTypesUtils.dateList.contains(psiClassChild.getName())){
            return paramList;
        }else if(psiClassChild.getName().startsWith("List") || psiClassChild.getName().startsWith("Set")){
            String sourceChildType = childGenericTypes.get(0);
            String childType = sourceChildType.split(">")[0];
            psiClassChild = JavaPsiFacade.getInstance(project).findClass(childType, GlobalSearchScope.allScope(project));
            childGenericTypes.remove(sourceChildType);
            return getParamListFromClass(psiClassChild,project,parentKey,paramKey,childGenericTypes);
        }else{
            PsiField[] allFields = psiClassChild.getAllFields();
            for (PsiField field : allFields) {
                if(MyPsiTypesUtils.skipFiled.contains(field.getName())){
                    continue;
                }
                List<RequestParam> params = getParamListFromFiled(field, project, parentKey, field.getName(), childGenericTypes);
                paramList.addAll(params);
            }
        }
        return paramList;
    }



    public static <T> List<T> singletonList(T t){
        List<T> list = new ArrayList<>();
        list.add(t);
        return list;
    }

    public static Integer getType(String presentableText) {
        if(presentableText.contains("String")){
            return 0;
        }
        if(presentableText.contains("JSON")){
            return 2;
        }
        if(presentableText.contains("Integer") || StringUtils.equals(presentableText,"int")){
            return 3;
        }
        if(presentableText.contains("Float") || StringUtils.equals(presentableText,"float")){
            return 4;
        }
        if(presentableText.contains("Double") || StringUtils.equals(presentableText,"double")){
            return 5;
        }
        if(presentableText.contains("Date") || StringUtils.equals(presentableText,"Time")){
            return 6;
        }
        if(presentableText.contains("Boolean") || StringUtils.equals(presentableText,"boolean")){
            return 8;
        }
        if(presentableText.contains("Byte") || StringUtils.equals(presentableText,"byte")){
            return 9;
        }
        if(presentableText.contains("Short") || StringUtils.equals(presentableText,"'short'")){
            return 10;
        }
        if(presentableText.contains("Long") || StringUtils.equals(presentableText,"long")){
            return 11;
        }
        if(presentableText.contains("List")
         || presentableText.contains("Set")
        || presentableText.contains("array")){
            return 12;
        }
        return 13;
    }

    public static String getType(Integer type) {
        switch (type){
            case 0:return "String";
            case 2:return "JSON";
            case 3:return "int";
            case 4:return "float";
            case 5:return "double";
            case 6:return "Time";
            case 8:return "boolean";
            case 9:return "byte";
            case 10:return "short";
            case 11:return "long";
            case 12:return "Collection";
            default:return "UNKNOWN";
        }
    }

    private static NotificationGroup notificationGroup;

    static {
        notificationGroup = new NotificationGroup("Java2Json.NotificationGroup", NotificationDisplayType.BALLOON, true);
    }


    public static void error(String message, Project project){
        Notification error = notificationGroup.createNotification(message, NotificationType.ERROR);
        Notifications.Bus.notify(error, project);
    }
    public static void success(String message, Project project){
        Notification error = notificationGroup.createNotification(message, NotificationType.INFORMATION);
        Notifications.Bus.notify(error, project);
    }

    public static String getParamKey(String parentKey,String paramKey){
        if(StringUtils.isBlank(parentKey)){
            return paramKey;
        }
        return parentKey+">>"+paramKey;
    }
}
