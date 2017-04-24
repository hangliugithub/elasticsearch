/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.common.text;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.CompiledScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.template.CompiledTemplate;
import org.elasticsearch.xpack.watcher.Watcher;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TextTemplateEngine extends AbstractComponent {

    private final ScriptService service;

    public TextTemplateEngine(Settings settings, ScriptService service) {
        super(settings);
        this.service = service;
    }

    public String render(TextTemplate textTemplate, Map<String, Object> model) {
        if (textTemplate == null) {
            return null;
        }

        String template = textTemplate.getTemplate();
        String mediaType = compileParams(detectContentType(template));
        template = trimContentType(textTemplate);

        Map<String, Object> mergedModel = new HashMap<>();
        if (textTemplate.getParams() != null) {
            mergedModel.putAll(textTemplate.getParams());
        }
        mergedModel.putAll(model);

        Map<String, String> options = null;
        if (textTemplate.getType() == ScriptType.INLINE) {
            options = new HashMap<>();

            if (textTemplate.getScript() != null && textTemplate.getScript().getOptions() != null) {
                options.putAll(textTemplate.getScript().getOptions());
            }

            options.put(Script.CONTENT_TYPE_OPTION, mediaType);
        }
        Script script = new Script(textTemplate.getType(), "mustache", template, options, mergedModel);
        CompiledTemplate compiledTemplate = service.compileTemplate(script, Watcher.SCRIPT_CONTEXT);
        return compiledTemplate.run(model).utf8ToString();
    }

    private String trimContentType(TextTemplate textTemplate) {
        String template = textTemplate.getTemplate();
        if (!template.startsWith("__")){
            return template; //Doesn't even start with __ so can't have a content type
        }
        // There must be a __<content_type__:: prefix so the minimum length before detecting '__::' is 3
        int index = template.indexOf("__::", 3);
        // Assume that the content type name is less than 10 characters long otherwise we may falsely detect strings that start with '__
        // and have '__::' somewhere in the content
        if (index >= 0 && index < 12) {
            if (template.length() == 6) {
                template = "";
            } else {
                template = template.substring(index + 4);
            }
        }
        return template;
    }

    private XContentType detectContentType(String content) {
        if (content.startsWith("__")) {
            //There must be a __<content_type__:: prefix so the minimum length before detecting '__::' is 3
            int endOfContentName = content.indexOf("__::", 3);
            if (endOfContentName != -1) {
                return XContentType.fromMediaTypeOrFormat(content.substring(2, endOfContentName));
            }
        }
        return null;
    }

    private String compileParams(XContentType contentType) {
        if (contentType == XContentType.JSON) {
            return "application/json";
        } else {
            return "text/plain";
        }
    }
}
