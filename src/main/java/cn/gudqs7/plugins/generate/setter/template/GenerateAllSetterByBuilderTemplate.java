package cn.gudqs7.plugins.generate.setter.template;

import cn.gudqs7.plugins.generate.base.BaseVar;
import cn.gudqs7.plugins.generate.base.GenerateBase;
import cn.gudqs7.plugins.generate.base.GenerateBaseTemplate;
import cn.gudqs7.plugins.generate.setter.GenerateBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.apache.commons.lang.StringUtils;

/**
 * @author WQ
 * @date 2021/10/1
 */
@SuppressWarnings("PostfixTemplateDescriptionNotFound")
public class GenerateAllSetterByBuilderTemplate extends GenerateBaseTemplate {

    private final boolean generateDefaultVal;

    public GenerateAllSetterByBuilderTemplate() {
        super("allbuilder", "Generate builder", GenerateAllSetterByBuilderTemplate::isApplicable0);
        this.generateDefaultVal = true;
    }

    private static boolean isApplicable0(PsiElement psiElement) {
        String methodName = "";
        if (psiElement instanceof PsiJavaToken) {
            PsiJavaToken psiJavaToken = (PsiJavaToken) psiElement;
            IElementType tokenType = psiJavaToken.getTokenType();
            String tokenTypeName = tokenType.toString();
            switch (tokenTypeName) {
                case "RPARENTH":
                    methodName = psiElement.getParent().getPrevSibling().getLastChild().getText();
                    psiElement = psiElement.getParent().getPrevSibling().getFirstChild();
                    break;
                case "SEMICOLON":
                    methodName = psiElement.getPrevSibling().getFirstChild().getLastChild().getText();
                    psiElement = psiElement.getPrevSibling().getFirstChild().getFirstChild();
                    break;
                default:
                    psiElement = psiElement.getParent().getParent();
                    break;
            }
        }
        if ("builder".equals(methodName)) {
            if (psiElement instanceof PsiReferenceExpression) {
                PsiReferenceExpression expression = (PsiReferenceExpression) psiElement;
                return StringUtils.isNotBlank(expression.getCanonicalText());
            }
        }
        return false;
    }

    @Override
    protected GenerateBase buildGenerate(PsiElement psiElement, PsiFile containingFile, PsiDocumentManager psiDocumentManager, Document document) {
        Project project = psiElement.getProject();
        if (psiElement instanceof PsiMethodCallExpression) {
            psiElement = psiElement.getFirstChild().getFirstChild();
        }
        if (psiElement instanceof PsiReferenceExpression) {
            PsiReferenceExpression expression = (PsiReferenceExpression) psiElement;
            String canonicalText = expression.getCanonicalText();
            PsiClassType psiType = PsiType.getTypeByName(canonicalText, project, GlobalSearchScope.allScope(project));
            BaseVar baseVar = new BaseVar();
            baseVar.setVarName(expression.getText());
            baseVar.setVarType(psiType);
            return new GenerateBuilder(generateDefaultVal, baseVar);
        }
        return null;
    }


}