/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.mailbox.jcr.mail;

import java.util.Optional;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jcr.MailboxSessionJCRRepository;
import org.apache.james.mailbox.jcr.mail.model.JCRMailbox;
import org.apache.james.mailbox.store.mail.AbstractLockingUidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class JCRUidProvider extends AbstractLockingUidProvider {

    private final MailboxSessionJCRRepository repository;

    public JCRUidProvider(MailboxPathLocker locker, MailboxSessionJCRRepository repository) {
        super(locker);
        this.repository = repository;
    }

    @Override
    public Optional<MessageUid> lastUid(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        try {
            Session s = repository.login(mailboxSession);
            Node node = s.getNodeByIdentifier(mailbox.getMailboxId().serialize());
            long rawUid = node.getProperty(JCRMailbox.LASTUID_PROPERTY).getLong();
            if (rawUid == 0) {
                return Optional.empty();
            }
            return Optional.of(MessageUid.of(rawUid));
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to get last uid for mailbox " + mailbox, e);
        }
        
    }

    @Override
    protected MessageUid lockedNextUid(MailboxSession session, Mailbox mailbox) throws MailboxException {
        try {
            Session s = repository.login(session);
            Node node = s.getNodeByIdentifier(mailbox.getMailboxId().serialize());
            MessageUid uid = MessageUid.of(node.getProperty(JCRMailbox.LASTUID_PROPERTY).getLong());
            MessageUid nextUid = uid.next();
            node.setProperty(JCRMailbox.LASTUID_PROPERTY, nextUid.asLong());
            s.save();
            return nextUid;
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to consume next uid for mailbox " + mailbox, e);
        }
    }
    


}
