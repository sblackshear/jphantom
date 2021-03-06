package jphantom.dataflow;

import java.util.*;
import org.objectweb.asm.tree.analysis.*;

import static util.Utils.union;
import static org.objectweb.asm.tree.analysis.BasicValue.UNINITIALIZED_VALUE;
import static jphantom.dataflow.TypeInterpreter.NULL_VALUE;

public abstract class CompoundValue implements Value
{
    private final Set<BasicValue> v;
    protected final Set<BasicValue> uv;

    private CompoundValue(Set<BasicValue> basic) {
        v = basic;
        uv = Collections.unmodifiableSet(v);
    }

    public Set<BasicValue> values() {
        assert !uv.isEmpty() || asBasicValue().equals(NULL_VALUE);
        return uv;
    }

    public boolean isEmpty() {
        return uv.isEmpty();
    }

    protected boolean contains(CompoundValue other) {
        return uv.containsAll(other.uv);
    }

    public abstract BasicValue asBasicValue();
    public abstract boolean isReference();


    ///////////////////// Simple Compound Value /////////////////////

    private static class SimpleCompoundValue extends CompoundValue {

        private final BasicValue val;

        private SimpleCompoundValue(BasicValue val) {
            super(makeSet(val));
            this.val = val;
        }

        private static Set<BasicValue> makeSet(BasicValue val) {
            return val.equals(NULL_VALUE) ? 
                Collections.<BasicValue>emptySet() : Collections.singleton(val);
        }

        @Override
        public BasicValue asBasicValue() {
            return val;
        }

        @Override
        public int getSize() {
            return val.getSize();
        }

        @Override
        public boolean isReference() {
            return val.isReference();
        }

        @Override
        public String toString() {
            return val.toString();
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
                return true;

            if (!(other instanceof SimpleCompoundValue))
                return false;

            final SimpleCompoundValue v = (SimpleCompoundValue) other;

            return val.equals(v.val);
        }

        @Override
        public int hashCode() {
            return val.hashCode();
        }
    }

    ///////////////////// Merged Compound Value /////////////////////

    private static class MergedCompoundValue extends CompoundValue {

        private final CompoundValue left;
        private final CompoundValue right;

        private MergedCompoundValue(CompoundValue left, CompoundValue right) {
            super(union(left.values(), right.values()));
            this.left = left;
            this.right = right;

            if (left.getSize() != right.getSize())
                throw new IllegalArgumentException(left + " " + right);

            if (!left.isReference())
                throw new IllegalArgumentException(left.toString());

            if (!right.isReference())
                throw new IllegalArgumentException(right.toString());

            // TODO: make sure that the following constraint is valid
            for ( BasicValue v : uv )
                if (v == UNINITIALIZED_VALUE)
                    throw new IllegalArgumentException(
                        "Uninitialized value participates in a merge!!!");
                else 
                    assert v.getType() != null;

            // Must be strictly increasing

            assert uv.size() > 1;
            assert uv.size() > left.values().size();
            assert uv.size() > right.values().size();
        }

        @Override
        public BasicValue asBasicValue() {
            return null;
        }

        @Override
        public int getSize() {
            return left.getSize();
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public String toString() {
            return "(" + left.toString() + "," + right.toString() + ")";
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
                return true;

            if (!(other instanceof MergedCompoundValue))
                return false;

            final MergedCompoundValue v = (MergedCompoundValue) other;

            return uv.equals(v.uv);
        }

        @Override
        public int hashCode() {
            return uv.hashCode();
        }
    }

    ///////////////////// Compound Value Factory /////////////////////

    public static CompoundValue fromBasicValue(BasicValue val) {
        return val == null ? null : new SimpleCompoundValue(val);
    }

    public static CompoundValue fromMerge(CompoundValue left, CompoundValue right)
    {
        if (left.equals(right))
            return left;

        if (!left.isReference() || !right.isReference()) {
            do {
                if (UNINITIALIZED_VALUE.equals(left.asBasicValue()))
                    break;

                if (UNINITIALIZED_VALUE.equals(right.asBasicValue()))
                    break;

                // TODO: check why this is not valid
                // throw new MergingException(left, right);
            } while (false);
            return fromBasicValue(UNINITIALIZED_VALUE);
        }

        if (left.isEmpty() || right.contains(left))
            return right;

        if (right.isEmpty() || left.contains(right))
            return left;

        return new MergedCompoundValue(left, right);
    }

    private static class MergingException extends RuntimeException {
        protected final static long serialVersionUID = 8347563458345634534L;

        private final CompoundValue left;
        private final CompoundValue right;

        MergingException(CompoundValue left, CompoundValue right)
        {
            super();
            this.left = left;
            this.right = right;
        }
    }
}


