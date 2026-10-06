package com.ecom.config;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IText;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.postprocessor.PostProcessor;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Strips comments from the inline {@code <script>} blocks of every rendered
 * page; see {@link JsCommentStripper}. HTML comments are already kept out of
 * the output by writing them as Thymeleaf comments ({@code <!--/* ... *&#47;-->}),
 * but those cannot be used inside a script.
 *
 * <p>Runs as a post-processor, on the finished output, so expressions inlined
 * into a script have already been resolved and are seen as the strings they
 * became.
 */
@Component
public class ScriptCommentDialect extends AbstractDialect implements IPostProcessorDialect {

    public ScriptCommentDialect() {
        super("script-comments");
    }

    @Override
    public int getDialectPostProcessorPrecedence() {
        return 1000;
    }

    @Override
    public Set<IPostProcessor> getPostProcessors() {
        return Set.of(new PostProcessor(TemplateMode.HTML, Handler.class, 1000));
    }

    /**
     * Collects the text of a script element and passes it on stripped when the
     * element closes. A script can reach this handler as more than one text
     * event, and a comment may straddle two of them.
     */
    public static final class Handler extends AbstractTemplateHandler {

        private StringBuilder script;

        @Override
        public void handleOpenElement(IOpenElementTag tag) {
            super.handleOpenElement(tag);
            if (isInlineJavaScript(tag)) {
                script = new StringBuilder();
            }
        }

        @Override
        public void handleText(IText text) {
            if (script != null) {
                script.append(text.getText());
            } else {
                super.handleText(text);
            }
        }

        @Override
        public void handleCloseElement(ICloseElementTag tag) {
            if (script != null && "script".equalsIgnoreCase(tag.getElementCompleteName())) {
                if (!script.isEmpty()) {
                    super.handleText(getContext().getModelFactory()
                            .createText(JsCommentStripper.strip(script.toString())));
                }
                script = null;
            }
            super.handleCloseElement(tag);
        }

        private static boolean isInlineJavaScript(IOpenElementTag tag) {
            if (!"script".equalsIgnoreCase(tag.getElementCompleteName()) || tag.hasAttribute("src")) {
                return false;
            }
            String type = tag.getAttributeValue("type");
            if (type == null || type.isBlank()) {
                return true;
            }
            type = type.trim().toLowerCase(Locale.ROOT);
            return type.equals("module") || type.equals("text/javascript") || type.equals("application/javascript");
        }
    }
}
