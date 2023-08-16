package com.jkqj.base.gateway.upstream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DubboUpstream extends Upstream {
    private String group;
    private String interfaceName;
    private String methodName;
    private String[] parameterTypes;
    private String[] parameterNames;
    private String version;

    public void setParameters(List<Parameter> parameters) {
        if (CollectionUtils.isEmpty(parameters)) {
            this.parameterTypes = new String[0];
            this.parameterNames = new String[0];
        } else {
            this.parameterTypes = new String[parameters.size()];
            this.parameterNames = new String[parameters.size()];
            for (int i = 0; i < parameters.size(); i++) {
                this.parameterTypes[i] = parameters.get(i).type;
                this.parameterNames[i] = parameters.get(i).name;
            }
        }
    }

    @Override
    public Upstreams getType() {
        return Upstreams.DUBBO;
    }

    @Data
    @AllArgsConstructor
    public static class Parameter {
        private String type;
        private String name;

        public Parameter(String[] typeAndName) {
            this.type = typeAndName[0];
            if (typeAndName.length == 2) {
                this.name = typeAndName[1];
            } else {
                this.name = null;
            }
        }
    }
}
