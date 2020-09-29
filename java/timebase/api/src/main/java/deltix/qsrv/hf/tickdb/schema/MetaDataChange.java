package deltix.qsrv.hf.tickdb.schema;

import deltix.qsrv.hf.pub.md.*;

import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;

public class MetaDataChange implements SchemaChange {

    protected MetaDataChange() { } // for jaxb

    protected ClassSet<RecordClassDescriptor> source;
    protected MetaData<RecordClassDescriptor> target;

    @XmlElement
    public EnumMapping enumMapping = new EnumMapping();

    @XmlElement
    public ContentType sourceType;
    @XmlElement
    public ContentType targetType;
    
    @XmlElement
    public SchemaMapping mapping; // predefined mappings

    @XmlElement
    public ArrayList<ClassDescriptorChange> changes = new ArrayList<ClassDescriptorChange>();

    public MetaDataChange(ClassSet<RecordClassDescriptor> source,
                          MetaData<RecordClassDescriptor> target,
                          SchemaMapping mapping) {
        this.source = source;
        this.target = target;
        this.mapping = mapping;
    }

    public ClassSet<RecordClassDescriptor>   getSource() {
        return source;
    }

    public Impact getChangeImpact() {
        if (sourceType != targetType)
            return Impact.DataLoss;

        Impact result = Impact.None;
        
        for (ClassDescriptorChange change : changes) {
            Impact imp = change.getChangeImpact(mapping);
            if (imp == Impact.DataLoss)
                return Impact.DataLoss;
            else if (imp == Impact.DataConvert)
                result = Impact.DataConvert;
        }

        return result;
    }   

    public ClassDescriptorChange getChange(ClassDescriptor source, ClassDescriptor target) {
        for (ClassDescriptorChange change : changes) {
            if (source != null && source.equals(change.getSource()))
                return change;
            if (target != null && target.equals(change.getTarget()))
                return change;
        }

        return null;
    }

    public void                 invalidate(
            ClassSet<RecordClassDescriptor> source,
            MetaData<RecordClassDescriptor> target)
    {
        this.source = source;
        this.target = target;
        
        for (ClassDescriptorChange change : changes) {
            if (change.getSource() == null && change.getSourceId() != null)
                change.source = getClassDescriptor(source, change.getSourceId());

            if (change.getTarget() == null && change.getTargetId() != null)
                change.target = getClassDescriptor(target, change.getTargetId());
        }
    }

    public static RecordClassDescriptor getClassDescriptor(ClassSet<RecordClassDescriptor> set, String guid) {
        
        ClassDescriptor[] classes = set.getClasses();

        for (int i = 0; i < classes.length; i++) {
            if (guid.equals(classes[i].getGuid()))
                return (RecordClassDescriptor) classes[i];
        }

//        Optional<ClassDescriptor> match = Stream.of(classes).filter(x -> guid.equals(x.getGuid())).findFirst();
//        return (RecordClassDescriptor) match.orElse(null);

        return null;
    }

    public MetaData<RecordClassDescriptor> getTarget() {
        return target;
    }

    /**
     * Indicates, that this change can be applied to metadata.
     */
    public boolean isAcceptable() {
        return getChangeImpact() != Impact.DataLoss;
    }

    public enum ContentType {
        Polymorphic, Fixed, Mixed
    }
}
