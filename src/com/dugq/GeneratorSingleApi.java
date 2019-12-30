package com.dugq;

import com.dugq.ams.ApiEditorService;
import com.dugq.ams.LoginService;
import com.dugq.pojo.EditorParam;
import com.dugq.pojo.GroupVo;
import com.dugq.pojo.RequestParam;
import com.dugq.pojo.SimpleApiVo;
import com.dugq.pojo.enums.RequestType;
import com.dugq.util.ApiParamBuildUtil;
import com.dugq.util.DesUtil;
import com.dugq.util.NormalTypes;
import com.dugq.util.Param2JSON;
import com.dugq.util.PsiAnnotationSearchUtil;
import com.dugq.util.SpringMVCConstant;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by dugq on 2019/12/16.
 */
public class GeneratorSingleApi extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        EditorParam param = new EditorParam();
        Project project = event.getProject();
        Editor editor = event.getData(PlatformDataKeys.EDITOR);
        if(Objects.isNull(editor)){
            ApiParamBuildUtil.error("请选择接口",project);
            return;
        }
        String login = LoginService.login(project);
        Boolean isLogin = LoginService.checkLogin(project,login);

        if(!isLogin){
            ApiParamBuildUtil.error("账号密码错误",project);
            return;
        }
        List<GroupVo> groupVos = ApiEditorService.allGroup(project);

        //获得光标所处的文件，和方法
        PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        if(Objects.isNull(psiFile)){
            ApiParamBuildUtil.error("请选择文件！",project);
            throw new RuntimeException();
        }
        //获取当前方法
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(psiFile.findElementAt(editor.getCaretModel().getOffset()), PsiMethod.class);
        if(Objects.isNull(containingMethod)){
            ApiParamBuildUtil.error("请选择方法！",project);
            throw new RuntimeException();
        }
        //获取当前类
        PsiClass containingClass = containingMethod.getContainingClass();
        if(Objects.isNull(containingClass)){
            ApiParamBuildUtil.error("请选择类！",project);
            throw new RuntimeException();
        }

        String mapping = getRequestUrl(containingClass, SpringMVCConstant.RequestMapping,project);
        if(Objects.isNull(mapping)){
            ApiParamBuildUtil.error("class 的 requestMapping有错",project);
            throw new RuntimeException();
        }
        String subMapping = getRequestUrl(containingMethod, SpringMVCConstant.GetMapping, project);
        String requestMethod;
        if(Objects.nonNull(subMapping)){
            requestMethod = "get";
        }else{
            subMapping = getRequestUrl(containingMethod, SpringMVCConstant.PostMapping, project);
            if(Objects.isNull(subMapping)){
                ApiParamBuildUtil.error("方法必须注解 getMapping or postMapping",project);
                throw new RuntimeException();
            }
            requestMethod = "post";
        }
        String uri = mapping+(subMapping.startsWith("/")?"":"/")+subMapping;
        param.setApiRequestType(RequestType.getByDesc(requestMethod).getType());
        param.setApiURI(uri);
        //读取方法注释
        PsiDocComment docComment = containingMethod.getDocComment();
        String methodDesc = DesUtil.getFiledDesc(docComment);
        if(StringUtils.isBlank(methodDesc)){
            ApiParamBuildUtil.error("接口名称请用在方法注释中声明",project);
            return;
        }
        //截取第一段作为接口注释
        if(methodDesc.contains("@")){
            methodDesc = methodDesc.substring(0,methodDesc.indexOf("@"));
        }
        param.setApiName(methodDesc);
        List<RequestParam> queryList = getQueryList(project, containingMethod);
        param.setApiRequestParam(queryList);
        List<RequestParam> returnList = getReturnList(project, containingMethod);
        param.setApiResultParam(returnList);
        param.setApiSuccessMock(Param2JSON.param2Json(returnList).toJSONString());
        List<SimpleApiVo> simpleApiVos = ApiEditorService.amsApiSearchParam(project, uri);
        if(CollectionUtils.isNotEmpty(simpleApiVos)){
            if(simpleApiVos.size()>1){
                ApiParamBuildUtil.error("存在多个相同URI的API，无法添加！！！",project);
                return;
            }
            int update = Messages.showDialog("请选择", "存在相同uri接口，是否跟新？", new String[]{"是", "否"}, 0, null);
            if (update==0){
                SimpleApiVo simpleApiVo = simpleApiVos.get(0);
                param.setGroupID(simpleApiVo.getGroupID());
                param.setApiID(simpleApiVo.getApiID());
                ApiEditorService.editAPI(project,param);
                ApiParamBuildUtil.success("跟新成功",project);
                return;
            }else{
                ApiParamBuildUtil.error("存在同名URI，无法添加！！！",project);
                return;
            }

        }
        GroupVo groupVo = getGroupVo(groupVos);
        param.setGroupID(groupVo.getGroupID());
        ApiEditorService.addAPI(project,param);
        ApiParamBuildUtil.success("上传接口成功",project);
    }

    private GroupVo getGroupVo(List<GroupVo> groupVos) {
        List<String> groupName = groupVos.stream().map(GroupVo::getGroupName).collect(Collectors.toList());
        int chooseGroup = Messages.showDialog("请选择", "选择分组", groupName.toArray(new String[]{}), 0, null);
        GroupVo groupVo = groupVos.get(chooseGroup);
        if(CollectionUtils.isNotEmpty(groupVo.getChildGroupList())){
            return getGroupVo(groupVo.getChildGroupList());
        }
        return groupVo;
    }

    private List<RequestParam> getReturnList(Project project, PsiMethod containingMethod) {
        List<RequestParam> returnList = new ArrayList<>();
        PsiType returnType = containingMethod.getReturnType();
        if(Objects.nonNull(returnType)){
            if(returnType instanceof PsiPrimitiveType){
                RequestParam returnValue = new RequestParam();
                returnValue.setParamKey(((PsiPrimitiveType) returnType).getName());
                returnValue.setParamName(DesUtil.getReturn(containingMethod));
                returnValue.setParamType(ApiParamBuildUtil.getType(returnType.getPresentableText()));
                returnList.add(returnValue);
            }
            else if(NormalTypes.isNormalType(returnType.getPresentableText())){
                RequestParam returnValue = new RequestParam();
                returnValue.setParamKey(((PsiPrimitiveType) returnType).getName());
                returnValue.setParamName(DesUtil.getReturn(containingMethod));
                returnValue.setParamType(ApiParamBuildUtil.getType(returnType.getPresentableText()));
                returnList.add(returnValue);
            }else if(returnType.getPresentableText().startsWith("List")
                    || returnType.getPresentableText().startsWith("Map")
                    || returnType.getPresentableText().startsWith("Set")
                    || returnType instanceof PsiArrayType) {
                ApiParamBuildUtil.error("返回值不支持直接返回集合",project);
                throw new RuntimeException();
            }else{
                String canonicalText = returnType.getCanonicalText();
                PsiClass returnClass;
                List<String> childClass = new ArrayList<>();
                if(canonicalText.contains("<")){
                    String[] classNames = canonicalText.split("<");
                    String fieldType = classNames[0];
                    for (int i = 1 ; i<classNames.length;i++ ) {
                        childClass.add(classNames[i]);
                    }
                    returnClass = JavaPsiFacade.getInstance(project).findClass(fieldType, GlobalSearchScope.allScope(project));
                }else{
                    returnClass = JavaPsiFacade.getInstance(project).findClass(canonicalText, GlobalSearchScope.allScope(project));
                }
                for (PsiField field : returnClass.getFields()) {
                    List<RequestParam> paramListFromFiled = ApiParamBuildUtil.getParamListFromFiled(field, project, null, field.getName(), childClass);
                    returnList.addAll(paramListFromFiled);
                }
            }
        }
        return returnList;
    }


    private List<RequestParam> getQueryList(Project project, PsiMethod containingMethod) {
        List<RequestParam> queryList = new ArrayList<>();
        PsiParameter[] parameters = containingMethod.getParameterList().getParameters();
        for (PsiParameter psiParameter : parameters) {
            if(NormalTypes.skipParams.contains(psiParameter.getType().getPresentableText())){
                continue;
            }
            if(NormalTypes.skipFiled.contains(psiParameter.getName())){
                continue;
            }
            List<RequestParam> query = getParam(project, psiParameter,containingMethod);
            queryList.addAll(query);
        }
        return queryList;
    }

    private List<RequestParam> getParam(Project project, PsiVariable psiParameter, PsiMethod containingMethod) {
        PsiType psiType = psiParameter.getType();
        if(psiType instanceof PsiPrimitiveType){
            //如果是基本类型
            RequestParam query = new RequestParam();
            query.setParamKey(psiParameter.getName());
            query.setParamType(ApiParamBuildUtil.getType(psiType.getPresentableText()));
            query.setParamName(DesUtil.getParamDesc(containingMethod,psiParameter.getName()));
            query.setParamValueList(DesUtil.getParamEnumValues(containingMethod,psiParameter.getName(),project));
            PsiAnnotation notNull = psiParameter.getAnnotation("javax.validation.constraints.NotNull");
            if(Objects.nonNull(notNull)){
                query.setParamNotNull(0);
            }
            return ApiParamBuildUtil.singletonList(query);
        }else if(NormalTypes.isNormalType(psiType.getPresentableText())){
            //如果是包装类型
            RequestParam query = new RequestParam();
            query.setParamKey(psiParameter.getName());
            query.setParamType(ApiParamBuildUtil.getType(psiType.getPresentableText()));
            query.setParamName(DesUtil.getParamDesc(containingMethod,psiParameter.getName()));
            query.setParamValueList(DesUtil.getParamEnumValues(containingMethod,psiParameter.getName(),project));
            PsiAnnotation notNull = psiParameter.getAnnotation("javax.validation.constraints.NotNull");
            if(Objects.nonNull(notNull)){
                query.setParamNotNull(0);
            }
            return ApiParamBuildUtil.singletonList(query);
        }else if(psiType.getPresentableText().startsWith("List") || psiType.getPresentableText().startsWith("Set")){
            String[] types= psiType.getCanonicalText().split("<");
            if(types.length>1){
                String childPackage=types[1].split(">")[0];
                RequestParam query = new RequestParam();
                query.setParamKey(psiParameter.getName());
                query.setParamType(ApiParamBuildUtil.getType(childPackage));
                query.setParamName(DesUtil.getParamDesc(containingMethod,psiParameter.getName()));
                List<RequestParam> requstParams = ApiParamBuildUtil.singletonList(query);
                PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(childPackage, GlobalSearchScope.allScope(project));
                requstParams.addAll(ApiParamBuildUtil.getParamListFromClass(psiClassChild,project,null,psiParameter.getName(), Arrays.asList(types)));
                return requstParams;
            }else{
                ApiParamBuildUtil.error("参数"+psiParameter.getName()+"未加泛型",project);
                throw new RuntimeException();
            }
        }else if(psiType.getPresentableText().startsWith("Map")){
            ApiParamBuildUtil.error("参数不支持Map",project);
            throw new RuntimeException();
        }else{ //object
            List<String> types = new ArrayList<>();
            PsiClass psiClassChild;
            if(psiType.getCanonicalText().contains("<")){
                String[] classNames = psiType.getCanonicalText().split("<");
                String fieldType = classNames[0];
                for (int i = 1 ; i<classNames.length;i++ ) {
                    types.add(classNames[i]);
                }
                psiClassChild = JavaPsiFacade.getInstance(project).findClass(fieldType, GlobalSearchScope.allScope(project));
            }else{
                psiClassChild = JavaPsiFacade.getInstance(project).findClass(psiType.getCanonicalText(), GlobalSearchScope.allScope(project));
            }
            return ApiParamBuildUtil.getParamListFromClass(psiClassChild,project,null,null, types);
        }
    }

    private String getType(String presentableText) {
        if(presentableText.contains("Integer") || StringUtils.equals(presentableText,"int")){
            return "int";
        }
        if(presentableText.contains("Long") || StringUtils.equals(presentableText,"long")){
            return "long";
        }
        if(presentableText.contains("Byte") || StringUtils.equals(presentableText,"byte")){
            return "byte";
        }
        if(presentableText.contains("String") || StringUtils.equals(presentableText,"String")){
            return "string";
        }
        if(presentableText.contains("Double") || StringUtils.equals(presentableText,"double")){
            return "double";
        }
        if(presentableText.contains("Float") || StringUtils.equals(presentableText,"float")){
            return "float";
        }
        if(presentableText.contains("Date") || StringUtils.equals(presentableText,"Time")){
            return "date";
        }
        return "object";
    }

    private String getRequestUrl(PsiModifierListOwner target, String fullNameAnnotation, Project project) {
        PsiAnnotation psiAnnotation= PsiAnnotationSearchUtil.findAnnotation(target, fullNameAnnotation);
        if(Objects.isNull(psiAnnotation)){
            return null;
        }
        PsiNameValuePair[] psiNameValuePairs= psiAnnotation.getParameterList().getAttributes();
        if(psiNameValuePairs.length>0){
            if(psiNameValuePairs[0].getLiteralValue()!=null) {
                return psiNameValuePairs[0].getLiteralValue();
            }else{
                PsiAnnotationMemberValue psiAnnotationMemberValue=psiAnnotation.findAttributeValue("value");
                if(psiAnnotationMemberValue.getReference()!=null){
                    PsiReference reference = psiAnnotationMemberValue.getReference();
                    PsiElement resolve = reference.resolve();
                    String text = resolve.getText();
                    String[] results= text.split("=");
                    return results[results.length - 1].split(";")[0].replace("\"", "").trim();
                }else{
                    ApiParamBuildUtil.error("Mapping定义有问题",project);
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}
