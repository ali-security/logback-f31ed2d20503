/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2022, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.model.processor.conditional;


import ch.qos.logback.core.util.OptionHelper;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.conditional.IfModel;
import ch.qos.logback.core.model.conditional.IfModel.BranchState;
import ch.qos.logback.core.model.processor.ModelHandlerBase;
import ch.qos.logback.core.model.processor.ModelHandlerException;
import ch.qos.logback.core.model.processor.ModelInterpretationContext;

public class IfModelHandler extends ModelHandlerBase {


    public static final String MISSING_JANINO_MSG = "Could not find Janino library on the class path. Skipping conditional processing.";
    public static final String MISSING_JANINO_SEE = "See also " + CoreConstants.CODES_URL + "#ifJanino";

    public static final String NEW_OPERATOR_DISALLOWED_MSG = "The 'condition' attribute may not contain the 'new' operator.";
    public static final String NEW_OPERATOR_DISALLOWED_SEE = "See also " + CoreConstants.CODES_URL + "#conditionNew";

    public static final String JANINO_CONDITION_DISABLED_MSG = "Janino-based evaluation of the 'condition' attribute is disabled for security reasons, see CVE-2026-13006.";
    public static final String JANINO_CONDITION_DISABLED_SEE = "See also " + CoreConstants.CODES_URL + "#conditionJanino";


    enum Branch {IF_BRANCH, ELSE_BRANCH; }
    
    IfModel ifModel = null;
    
    public IfModelHandler(Context context) {
        super(context);
    }

    static public ModelHandlerBase makeInstance(Context context, ModelInterpretationContext ic) {
        return new IfModelHandler(context);
    }

    @Override
    protected Class<IfModel> getSupportedModelClass() {
        return IfModel.class;
    }
    
    @Override
    public void handle(ModelInterpretationContext mic, Model model) throws ModelHandlerException {
        
        ifModel = (IfModel) model;
        mic.pushModel(ifModel);

        // CVE-2026-13006: Janino-evaluated conditions are refused; neither branch runs.
        if (!OptionHelper.isNullOrEmptyOrAllSpaces(ifModel.getCondition())) {
            addError(JANINO_CONDITION_DISABLED_MSG + " Skipping <if> element on line " + model.getLineNumber());
            addError(JANINO_CONDITION_DISABLED_SEE);
            ifModel.setBranchState(BranchState.IN_ERROR);
        }
    }

    @Override
    public void postHandle(ModelInterpretationContext mic, Model model) throws ModelHandlerException {

        if(mic.isModelStackEmpty()) {
            addError("Unexpected unexpected empty model stack.");
            return;
        }

        Object o = mic.peekModel();
        if (o != ifModel) {
            addWarn("The object [" + o + "] on the top the of the stack is not the expected [" + ifModel);
        } else {
            mic.popModel();
        }
    }

}
