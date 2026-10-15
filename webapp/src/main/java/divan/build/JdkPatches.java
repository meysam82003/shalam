package divan.build;

import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * Build-time only (TeaVM compiler plugin, never part of the page): adds the few JDK methods the app calls that
 * TeaVM's class library lacks. Runtime.maxMemory sizes the app's drawing cache.
 */
public final class JdkPatches implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        host.add(new ClassHolderTransformer() {
            @Override
            public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
                if (!cls.getName().equals("java.lang.Runtime")) return;
                if (cls.getMethod(new MethodDescriptor("maxMemory", ValueType.LONG)) != null) return;
                MethodHolder m = new MethodHolder("maxMemory", ValueType.LONG);
                m.setLevel(AccessLevel.PUBLIC);
                ProgramEmitter pe = ProgramEmitter.create(m, context.getHierarchy());
                pe.constant(512L << 20).returnValue();
                cls.addMethod(m);
            }
        });
    }
}
