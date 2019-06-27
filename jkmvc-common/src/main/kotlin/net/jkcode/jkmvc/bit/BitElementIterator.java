package net.jkcode.jkmvc.bit;

import java.util.BitSet;
import java.util.Iterator;

/**
 * 比特对应元素的迭代器
 *
 * @author shijianhang
 * @date 2019-06-27 11:58 AM
 */
public class BitElementIterator<E> implements Iterator<E> {

    protected BitSet bits;

    protected IBitElementOperator<E> op;

    protected int curr = -1;

    public BitElementIterator(BitSet bits, IBitElementOperator<E> op) {
        this.bits = bits;
        this.op = op;
    }

    @Override
    public boolean hasNext() {
        return bits.nextSetBit(curr + 1) >= 0;
    }

    @Override
    public E next() {
        curr = bits.nextSetBit(curr + 1);
        return op.getElement(curr);
    }

    @Override
    public void remove() {
        if (curr != -1)
            op.removeElement(curr);
    }
}
