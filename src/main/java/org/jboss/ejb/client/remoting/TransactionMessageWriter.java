/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.ejb.client.remoting;

import java.io.DataOutput;
import java.io.IOException;

import org.jboss.ejb.client.TransactionID;

/**
 * User: jpai
 */
class TransactionMessageWriter extends AbstractMessageWriter {


    private static final byte HEADER_TX_COMMIT_MESSAGE = 0x0F;
    private static final byte HEADER_TX_ROLLBACK_MESSAGE = 0x10;
    private static final byte HEADER_TX_PREPARE_MESSAGE = 0x11;
    private static final byte HEADER_TX_FORGET_MESSAGE = 0x12;
    private static final byte HEADER_TX_BEFORE_COMPLETION_MESSAGE = 0x13;


    void writeTxCommit(final DataOutput output, final short invocationId, final TransactionID transactionID, final boolean onePhaseCommit) throws IOException {
        // write the header
        output.writeByte(HEADER_TX_COMMIT_MESSAGE);
        // write the invocation id
        output.writeShort(invocationId);
        final byte[] transactionIDBytes = transactionID.getEncodedForm();
        // write the length of the transaction id bytes
        PackedInteger.writePackedInteger(output, transactionIDBytes.length);
        // write the transaction id bytes
        output.write(transactionIDBytes);
        // write the "bit" indicating whether this is a one-phase commit
        output.writeBoolean(onePhaseCommit);
    }

    void writeTxRollback(final DataOutput output, final short invocationId, final TransactionID transactionID) throws IOException {
        // write the header
        output.writeByte(HEADER_TX_ROLLBACK_MESSAGE);
        // write the invocation id
        output.writeShort(invocationId);
        final byte[] transactionIDBytes = transactionID.getEncodedForm();
        // write the length of the transaction id bytes
        PackedInteger.writePackedInteger(output, transactionIDBytes.length);
        // write the transaction id bytes
        output.write(transactionIDBytes);
    }

    void writeTxPrepare(final DataOutput output, final short invocationId, final TransactionID transactionID) throws IOException {
        // write the header
        output.writeByte(HEADER_TX_PREPARE_MESSAGE);
        // write the invocation id
        output.writeShort(invocationId);
        final byte[] transactionIDBytes = transactionID.getEncodedForm();
        // write the length of the transaction id bytes
        PackedInteger.writePackedInteger(output, transactionIDBytes.length);
        // write the transaction id bytes
        output.write(transactionIDBytes);
    }

    void writeTxForget(final DataOutput output, final short invocationId, final TransactionID transactionID) throws IOException {
        // write the header
        output.writeByte(HEADER_TX_FORGET_MESSAGE);
        // write the invocation id
        output.writeShort(invocationId);
        final byte[] transactionIDBytes = transactionID.getEncodedForm();
        // write the length of the transaction id bytes
        PackedInteger.writePackedInteger(output, transactionIDBytes.length);
        // write the transaction id bytes
        output.write(transactionIDBytes);
    }

    void writeTxBeforeCompletion(final DataOutput output, final short invocationId, final TransactionID transactionID) throws IOException {
        // write the header
        output.writeByte(HEADER_TX_BEFORE_COMPLETION_MESSAGE);
        // write the invocation id
        output.writeShort(invocationId);
        final byte[] transactionIDBytes = transactionID.getEncodedForm();
        // write the length of the transaction id bytes
        PackedInteger.writePackedInteger(output, transactionIDBytes.length);
        // write the transaction id bytes
        output.write(transactionIDBytes);
    }
}
