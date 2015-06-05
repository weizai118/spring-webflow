/*
 * Copyright 2004-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.binding.expression.spel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.binding.expression.EvaluationException;
import org.springframework.binding.expression.Expression;
import org.springframework.binding.expression.PropertyNotFoundException;
import org.springframework.binding.expression.ValueCoercionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.util.Assert;

/**
 * A wrapper for a Spring EL {@link org.springframework.expression.Expression}
 * allowing it to be used under the Spring Binding {@link Expression} abstraction.
 *
 * @author Rossen Stoyanchev
 * @since 2.1.0
 */
public class SpringELExpression implements Expression {

	private final org.springframework.expression.Expression expression;

	private final Class<?> expectedType;

	private final Map<String, Expression> expressionVariables;

	private final ConversionService conversionService;

	private final List<PropertyAccessor> propertyAccessors;

	/**
	 * Constructor for SpringELExpression.
	 *
	 * @param expression a parsed Spring EL expression instance. Must not be null.
	 * @param expressionVariables provides a mapping between variables names and
	 * parsed Spring EL expression instances.
	 * This parameter is optional (may be null).
	 * @param expectedType the target type expected from the evaluation of the expression or null.
	 * This parameter is optional (may be null).
	 * @param conversionService the Spring ConversionService instance to use for type conversion
	 * @param propertyAccessors propertyAccessors for Spring EL to use when evaluating expressions
	 */
	public SpringELExpression(org.springframework.expression.Expression expression,
			Map<String, Expression> expressionVariables, Class<?> expectedType, ConversionService conversionService,
			List<PropertyAccessor> propertyAccessors) {

		Assert.notNull(expression, "The SpelExpression is required for evaluation");
		this.expression = expression;
		this.expressionVariables = expressionVariables;
		this.expectedType = expectedType;
		this.conversionService = conversionService;
		this.propertyAccessors = propertyAccessors;
	}

	public String getExpressionString() {
		return expression.getExpressionString();
	}

	public Object getValue(Object rootObject) throws EvaluationException {
		try {
			return expression.getValue(createEvaluationContext(rootObject), expectedType);
		} catch (SpelEvaluationException e) {
			if (e.getMessageCode().equals(SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE)) {
				throw new PropertyNotFoundException(rootObject.getClass(), getExpressionString(), e);
			}
			if (e.getMessageCode().equals(SpelMessage.TYPE_CONVERSION_ERROR)) {
				throw new ValueCoercionException(rootObject.getClass(), getExpressionString(), null, expectedType, e);
			}
			throw new EvaluationException(rootObject.getClass(), expression.getExpressionString(),
					"An ELException occurred getting the value for expression '" + getExpressionString()
							+ "' on context [" + rootObject.getClass() + "]", e);
		}
	}

	public Class<?> getValueType(Object rootObject) throws EvaluationException {
		try {
			return expression.getValueType(createEvaluationContext(rootObject));
		} catch (SpelEvaluationException e) {
			if (e.getMessageCode().equals(SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE)) {
				throw new PropertyNotFoundException(rootObject.getClass(), getExpressionString(), e);
			}
			throw new EvaluationException(rootObject.getClass(), getExpressionString(),
					"An ELException occurred getting the value type for expression '" + getExpressionString()
							+ "' on context [" + rootObject.getClass() + "]", e);
		}
	}

	public void setValue(Object rootObject, Object value) throws EvaluationException {
		try {
			StandardEvaluationContext evaluationContext = createEvaluationContext(rootObject);
			expression.setValue(evaluationContext, value);
		} catch (SpelEvaluationException e) {
			if (e.getMessageCode().equals(SpelMessage.PROPERTY_OR_FIELD_NOT_WRITABLE)) {
				throw new PropertyNotFoundException(rootObject.getClass(), getExpressionString(), e);
			}
			if (e.getMessageCode().equals(SpelMessage.EXCEPTION_DURING_PROPERTY_WRITE)) {
				throw new ValueCoercionException(rootObject.getClass(), getExpressionString(), value, expectedType, e);
			}
			throw new EvaluationException(rootObject.getClass(), getExpressionString(),
					"An ELException occurred setting the value of expression '" + getExpressionString()
							+ "' on context [" + rootObject.getClass() + "] to [" + value + "]", e);
		}
	}

	/**
	 * Create a new Spring EL evaluation context for the given rootObject.
	 */
	private StandardEvaluationContext createEvaluationContext(Object rootObject) {
		StandardEvaluationContext context = new StandardEvaluationContext(rootObject);
		context.setVariables(getVariableValues(rootObject));
		context.setTypeConverter(new StandardTypeConverter(conversionService));
		context.getPropertyAccessors().addAll(propertyAccessors);
		extendEvaluationContext(context);
		return context;
	}

	/**
	 * Invoked every time an evaluation context is created allowing further
	 * initialization from sub-classes.
	 */
	protected void extendEvaluationContext(StandardEvaluationContext context) {
	}

	/**
	 * Turn the map of variable-names-to-expressions into a map of variable-names-to-plain-objects
	 * by evaluating each object against the input rootObject.
	 *
	 * @param rootObject the Object to evaluate variable expressions against.
	 * @return a mapping between variables names and plain Object's.
	 */
	private Map<String, Object> getVariableValues(Object rootObject) {
		if (expressionVariables == null) {
			return Collections.emptyMap();
		}
		Map<String, Object> variableValues = new HashMap<String, Object>(expressionVariables.size());
		for (Map.Entry<String, Expression> var : expressionVariables.entrySet()) {
			variableValues.put(var.getKey(), var.getValue().getValue(rootObject));
		}
		return variableValues;
	}

	public String toString() {
		return getExpressionString();
	}

}
