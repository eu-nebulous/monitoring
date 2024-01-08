package eu.nebulous.ems.translate.analyze;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "create")
public class NamesKey {
    public final String parent;
    public final String child;

    public static NamesKey create(String s) {
        if (!isFullName(s))
            throw new IllegalArgumentException("Argument is not a full name (i.e. <parent-name>.<child-name>): "+s);
        String[] part = s.split("\\.");
        if (part.length!=2)
            throw new IllegalArgumentException("Argument is not a valid full name (i.e. <parent-name>.<child-name>): "+s);
        if (StringUtils.isBlank(part[0]) || StringUtils.isBlank(part[1]))
            throw new IllegalArgumentException("Argument is not a valid full name. Parent or Child name is blank: "+s);
        return create(part[0], part[1]);
    }

    public static boolean isFullName(String s) {
        if (StringUtils.isBlank(s)) return false;
        return s.contains(".");
    }

    public String name() {
        return parent+"."+child;
    }
}
