/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.sql.planner.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.trino.metadata.TableFunctionHandle;
import io.trino.spi.ptf.Argument;
import io.trino.sql.planner.Symbol;

import javax.annotation.concurrent.Immutable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

@Immutable
public class TableFunctionNode
        extends PlanNode
{
    private final String name;
    private final Map<String, Argument> arguments;
    private final List<Symbol> properOutputs;
    private final List<PlanNode> sources;
    private final List<TableArgumentProperties> tableArgumentProperties;
    private final List<List<String>> copartitioningLists;
    private final TableFunctionHandle handle;

    @JsonCreator
    public TableFunctionNode(
            @JsonProperty("id") PlanNodeId id,
            @JsonProperty("name") String name,
            @JsonProperty("arguments") Map<String, Argument> arguments,
            @JsonProperty("properOutputs") List<Symbol> properOutputs,
            @JsonProperty("sources") List<PlanNode> sources,
            @JsonProperty("tableArgumentProperties") List<TableArgumentProperties> tableArgumentProperties,
            @JsonProperty("copartitioningLists") List<List<String>> copartitioningLists,
            @JsonProperty("handle") TableFunctionHandle handle)
    {
        super(id);
        this.name = requireNonNull(name, "name is null");
        this.arguments = ImmutableMap.copyOf(arguments);
        this.properOutputs = ImmutableList.copyOf(properOutputs);
        this.sources = ImmutableList.copyOf(sources);
        this.tableArgumentProperties = ImmutableList.copyOf(tableArgumentProperties);
        this.copartitioningLists = copartitioningLists.stream()
                .map(ImmutableList::copyOf)
                .collect(toImmutableList());
        this.handle = requireNonNull(handle, "handle is null");
    }

    @JsonProperty
    public String getName()
    {
        return name;
    }

    @JsonProperty
    public Map<String, Argument> getArguments()
    {
        return arguments;
    }

    @JsonProperty
    public List<Symbol> getProperOutputs()
    {
        return properOutputs;
    }

    @JsonProperty
    public List<TableArgumentProperties> getTableArgumentProperties()
    {
        return tableArgumentProperties;
    }

    @JsonProperty
    public List<List<String>> getCopartitioningLists()
    {
        return copartitioningLists;
    }

    @JsonProperty
    public TableFunctionHandle getHandle()
    {
        return handle;
    }

    @JsonProperty
    @Override
    public List<PlanNode> getSources()
    {
        return sources;
    }

    @Override
    public List<Symbol> getOutputSymbols()
    {
        ImmutableList.Builder<Symbol> symbols = ImmutableList.builder();

        symbols.addAll(properOutputs);

        for (int i = 0; i < sources.size(); i++) {
            TableArgumentProperties sourceProperties = tableArgumentProperties.get(i);
            if (sourceProperties.isPassThroughColumns()) {
                symbols.addAll(sources.get(i).getOutputSymbols());
            }
            else {
                sourceProperties.getSpecification()
                        .map(DataOrganizationSpecification::getPartitionBy)
                        .ifPresent(symbols::addAll);
            }
        }

        return symbols.build();
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context)
    {
        return visitor.visitTableFunction(this, context);
    }

    @Override
    public PlanNode replaceChildren(List<PlanNode> newSources)
    {
        checkArgument(sources.size() == newSources.size(), "wrong number of new children");
        return new TableFunctionNode(getId(), name, arguments, properOutputs, newSources, tableArgumentProperties, copartitioningLists, handle);
    }

    public static class TableArgumentProperties
    {
        private final String argumentName;
        private final Multimap<String, Symbol> columnMapping;
        private final boolean rowSemantics;
        private final boolean pruneWhenEmpty;
        private final boolean passThroughColumns;
        private final Optional<DataOrganizationSpecification> specification;

        @JsonCreator
        public TableArgumentProperties(
                @JsonProperty("argumentName") String argumentName,
                @JsonProperty("columnMapping") Multimap<String, Symbol> columnMapping,
                @JsonProperty("rowSemantics") boolean rowSemantics,
                @JsonProperty("pruneWhenEmpty") boolean pruneWhenEmpty,
                @JsonProperty("passThroughColumns") boolean passThroughColumns,
                @JsonProperty("specification") Optional<DataOrganizationSpecification> specification)
        {
            this.argumentName = requireNonNull(argumentName, "argumentName is null");
            this.columnMapping = ImmutableMultimap.copyOf(columnMapping);
            this.rowSemantics = rowSemantics;
            this.pruneWhenEmpty = pruneWhenEmpty;
            this.passThroughColumns = passThroughColumns;
            this.specification = requireNonNull(specification, "specification is null");
        }

        @JsonProperty
        public String getArgumentName()
        {
            return argumentName;
        }

        @JsonProperty
        public Multimap<String, Symbol> getColumnMapping()
        {
            return columnMapping;
        }

        @JsonProperty
        public boolean isRowSemantics()
        {
            return rowSemantics;
        }

        @JsonProperty
        public boolean isPruneWhenEmpty()
        {
            return pruneWhenEmpty;
        }

        @JsonProperty
        public boolean isPassThroughColumns()
        {
            return passThroughColumns;
        }

        @JsonProperty
        public Optional<DataOrganizationSpecification> getSpecification()
        {
            return specification;
        }
    }
}
