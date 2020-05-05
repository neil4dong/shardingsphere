package org.apache.shardingsphere.sql.parser.binder.segment.insert.values;

import lombok.Getter;
import lombok.ToString;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.assignment.AssignmentSegment;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.column.ColumnSegment;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.expr.ExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.expr.simple.LiteralExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.expr.simple.ParameterMarkerExpressionSegment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@ToString
public class OnDuplicateUpdateContext {
    private final int parametersCount;

    private final List<ExpressionSegment> valueExpressions;

    private final List<Object> parameters;

    private final List<ColumnSegment> columns;

    public OnDuplicateUpdateContext(final Collection<AssignmentSegment> assignments, final List<Object> parameters, final int parametersOffset) {
        List<ExpressionSegment> expressionSegments = assignments.stream().map(AssignmentSegment::getValue).collect(Collectors.toList());
        parametersCount = calculateParametersCount(expressionSegments);
        valueExpressions = getValueExpressions(expressionSegments);
        this.parameters = getParameters(parameters, parametersOffset);
        columns = assignments.stream().map(AssignmentSegment::getColumn).collect(Collectors.toList());
    }

    private int calculateParametersCount(final Collection<ExpressionSegment> assignments) {
        int result = 0;
        for (ExpressionSegment each : assignments) {
            if (each instanceof ParameterMarkerExpressionSegment) {
                result++;
            }
        }
        return result;
    }

    private List<ExpressionSegment> getValueExpressions(final Collection<ExpressionSegment> assignments) {
        List<ExpressionSegment> result = new ArrayList<>(assignments.size());
        result.addAll(assignments);
        return result;
    }

    private List<Object> getParameters(final List<Object> parameters, final int parametersOffset) {
        if (0 == parametersCount) {
            return Collections.emptyList();
        }
        List<Object> result = new ArrayList<>(parametersCount);
        result.addAll(parameters.subList(parametersOffset, parametersOffset + parametersCount));
        return result;
    }

    /**
     * Get value.
     *
     * @param index index
     * @return value
     */
    public Object getValue(final int index) {
        ExpressionSegment valueExpression = valueExpressions.get(index);
        return valueExpression instanceof ParameterMarkerExpressionSegment ? parameters.get(getParameterIndex(valueExpression)) : ((LiteralExpressionSegment) valueExpression).getLiterals();
    }

    private int getParameterIndex(final ExpressionSegment valueExpression) {
        int result = 0;
        for (ExpressionSegment each : valueExpressions) {
            if (valueExpression == each) {
                return result;
            }
            if (each instanceof ParameterMarkerExpressionSegment) {
                result++;
            }
        }
        throw new IllegalArgumentException("Can not get parameter index.");
    }

    /**
     * get on duplicate key update column by index of this clause.
     * @param index index.
     * @return columnSegment
     */
    public ColumnSegment getColumn(final int index) {
        return columns.get(index);
    }
}
