/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.manager.MailboxManagerFixture;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

public abstract class AbstractCombinationManagerTest {
    private static final Flags FLAGS = new Flags();
    private static final byte[] MAIL_CONTENT = "Subject: test\r\n\r\ntestmail".getBytes();
    private static final int DEFAULT_MAXIMUM_LIMIT = 256;

    private MailboxManager mailboxManager;
    private MessageIdManager messageIdManager;
    private MessageManager messageManager1;
    private MessageManager messageManager2;

    private MailboxSession session;
    private Mailbox mailbox1;
    private Mailbox mailbox2;

    private CombinationManagerTestSystem testingData;

    public abstract CombinationManagerTestSystem createTestingData() throws Exception ;

    @Before
    public void setUp() throws Exception {
        session = new MockMailboxSession(MailboxManagerFixture.USER);
        testingData = createTestingData();

        mailbox1 = testingData.createMailbox(MailboxManagerFixture.MAILBOX_PATH1, session);
        mailbox2 = testingData.createMailbox(MailboxManagerFixture.MAILBOX_PATH2, session);

        mailboxManager = testingData.getMailboxManager();
        messageIdManager = testingData.getMessageIdManager();
        messageManager1 = testingData.createMessageManager(mailbox1, session);
        messageManager2 = testingData.createMessageManager(mailbox2, session);
    }

    @After
    public void tearDown() throws Exception {
        testingData.clean();
    }

    @Test
    public void getMessageCountFromMessageManagerShouldReturnDataSetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMessageCount(session)).isEqualTo(1);
    }

    @Test
    public void searchFromMessageManagerShouldReturnMessagesUsingSetInMailboxesFromMessageIdManager() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());

        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.search(query, session)).hasSize(1);
    }

    @Test
    public void searchFromMailboxManagerShouldReturnMessagesUsingSetInMailboxesFromMessageIdManagerWhenSearchByMailboxQueryWithMailboxPath() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(MailboxManagerFixture.MAILBOX_PATH1)
            .build();
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(mailboxManager.search(mailboxQuery, session)).hasSize(1)
            .extractingResultOf("getId")
            .containsOnly(mailbox1.getMailboxId());
    }

    @Test
    public void searchFromMailboxManagerShouldReturnMessagesUsingSetInMailboxesFromMessageIdManagerWhenSearchByMailboxQueryWithUsername() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .username(MailboxManagerFixture.USER)
            .expression(String.valueOf(MailboxQuery.FREEWILDCARD))
            .build();
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(mailboxManager.search(mailboxQuery, session)).hasSize(2)
            .extractingResultOf("getId")
            .containsOnly(mailbox1.getMailboxId(), mailbox2.getMailboxId());
    }

    @Test
    public void searchFromMailboxManagerShouldReturnMessagesUsingSetInMailboxesFromMessageIdManagerWhenSearchByMultiMailboxes() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());

        MultimailboxesSearchQuery.Builder builder = MultimailboxesSearchQuery.from(query);
        builder.inMailboxes(mailbox1.getMailboxId(), mailbox2.getMailboxId());
        MultimailboxesSearchQuery multiMailboxesQuery = builder.build();

        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT)).containsOnly(messageId);
    }

    @Test
    public void setFlagsToDeleteThenExpungeFromMessageManagerThenGetMessageFromMessageIdManagerShouldNotReturnAnything() throws Exception {
        Flags deleted = new Flags(Flag.DELETED);
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageManager1.setFlags(deleted, FlagsUpdateMode.ADD, MessageRange.all(), session);
        messageManager1.expunge(MessageRange.all(), session);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session)).isEmpty();
    }

    @Test
    public void expungeFromMessageManagerShouldWorkWhenSetFlagsToDeletedWithMessageIdManager() throws Exception {
        Flags deleted = new Flags(Flag.DELETED);
        ComposedMessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS);

        messageIdManager.setFlags(deleted, FlagsUpdateMode.ADD, messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(messageManager1.expunge(MessageRange.all(), session)).containsOnly(messageId.getUid());
    }

    @Test
    public void expungeFromMessageManagerShouldWorkWhenSetInMailboxesAMessageWithDeletedFlag() throws Exception { //I can mark as DELETED + expunge an mail with setInMbxs
        Flags deleted = new Flags(Flag.DELETED);
        ComposedMessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, deleted);

        messageIdManager.setInMailboxes(messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(messageManager1.expunge(MessageRange.all(), session)).containsOnly(messageId.getUid());
    }

    @Test
    public void getMessageFromMessageIdManagerShouldReturnMessageWhenAppendMessageFromMessageManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session)).hasSize(1);
    }

    @Test
    public void getMessageFromMessageIdManagerShouldReturnMessageWhenCopyMessageWithMailboxIdFromMailboxManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        mailboxManager.copyMessages(MessageRange.all(), mailbox1.getMailboxId(), mailbox2.getMailboxId(), session);

        List<MessageResult> listMessages = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);

        assertThat(listMessages).hasSize(2)
            .extractingResultOf("getMailboxId")
            .containsOnly(mailbox1.getMailboxId(), mailbox2.getMailboxId());
    }

    @Test
    public void getMessageFromMessageIdManagerShouldReturnMessageWhenCopyMessageWithMailboxPathFromMailboxManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        mailboxManager.copyMessages(MessageRange.all(), MailboxManagerFixture.MAILBOX_PATH1, MailboxManagerFixture.MAILBOX_PATH2, session);

        List<MessageResult> listMessages = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);

        assertThat(listMessages).hasSize(2)
            .extractingResultOf("getMailboxId")
            .containsOnly(mailbox1.getMailboxId(), mailbox2.getMailboxId());
    }

    @Test
    public void getMessageFromMessageIdManagerShouldReturnMessageWhenMoveMessageWithMailboxIdFromMailboxManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        mailboxManager.moveMessages(MessageRange.all(), MailboxManagerFixture.MAILBOX_PATH1, MailboxManagerFixture.MAILBOX_PATH2, session);

        List<MessageResult> listMessages = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);

        assertThat(listMessages).hasSize(1)
            .extractingResultOf("getMailboxId")
            .containsOnly(mailbox2.getMailboxId());
    }

    @Test
    public void getMessagesFromMessageManagerShouldReturnMessagesCreatedBySetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL, session)).hasSize(1);
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnRecentMessageWhenSetInMailboxesFromMessageIdManager() throws Exception {
        Flags recent = new Flags(Flag.RECENT);
        ComposedMessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, recent);

        long mailbox2NextUid = messageManager2.getMetaData(true, session, FetchGroup.UNSEEN_COUNT).getUidNext().asLong();
        messageIdManager.setInMailboxes(messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        List<MessageUid> messageUids = messageManager2.getMetaData(true, session, FetchGroup.UNSEEN_COUNT).getRecent();

        assertThat(messageUids).hasSize(1);
        assertThat(messageUids.get(0).asLong()).isGreaterThanOrEqualTo(mailbox2NextUid);
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnNumberOfRecentMessageWhenSetInMailboxesFromMessageIdManager() throws Exception {
        Flags recent = new Flags(Flag.RECENT);
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, recent).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(true, session, FetchGroup.FIRST_UNSEEN).countRecent()).isEqualTo(1);
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnUidNextWhenSetInMailboxesFromMessageIdManager() throws Exception {
        Flags recent = new Flags(Flag.RECENT);
        ComposedMessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, recent);

        messageIdManager.setInMailboxes(messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        List<MessageResult> listMessages = messageIdManager.getMessages(ImmutableList.of(messageId.getMessageId()), FetchGroupImpl.MINIMAL, session);

        long uid2 = FluentIterable.from(listMessages)
            .filter(messageInMailbox2())
            .get(0)
            .getUid()
            .asLong();

        assertThat(messageManager2.getMetaData(true, session, FetchGroup.FIRST_UNSEEN).getUidNext().asLong())
            .isGreaterThan(uid2);
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnHighestModSeqWhenSetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(true, session, FetchGroup.FIRST_UNSEEN).getHighestModSeq()).isNotNegative();
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnMessageCountWhenSetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(true, session, FetchGroup.FIRST_UNSEEN).getMessageCount()).isEqualTo(1);
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnNumberOfUnseenMessageWhenSetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(true, session, FetchGroup.UNSEEN_COUNT).getUnseenCount()).isEqualTo(1);
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnFirstUnseenMessageWhenSetInMailboxesFromMessageIdManager() throws Exception {
        ComposedMessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS);

        messageIdManager.setInMailboxes(messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(true, session, FetchGroup.FIRST_UNSEEN).getFirstUnseen()).isEqualTo(messageId.getUid());
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnNumberOfUnseenMessageWhenSetFlagsFromMessageIdManager() throws Exception {
        Flags newFlag = new Flags(Flag.RECENT);
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setFlags(newFlag, FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager1.getMetaData(true, session, FetchGroup.UNSEEN_COUNT).getUnseenCount()).isEqualTo(1);
    }

    @Test
    public void getMetadataFromMessageManagerShouldReturnFirstUnseenMessageWhenSetFlagsFromMessageIdManager() throws Exception {
        Flags newFlag = new Flags(Flag.USER);
        ComposedMessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS);

        messageIdManager.setFlags(newFlag, FlagsUpdateMode.ADD, messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager1.getMetaData(true, session, FetchGroup.FIRST_UNSEEN).getFirstUnseen()).isEqualTo(messageId.getUid());
    }

    @Test
    public void setInMailboxesFromMessageIdManagerShouldMoveMessage() throws Exception {
        MessageId messageId = messageManager1.appendMessage(new ByteArrayInputStream(MAIL_CONTENT), new Date(), session, false, FLAGS).getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        assertThat(messageManager1.getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL, session)).isEmpty();
        assertThat(messageManager2.getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL, session))
            .hasSize(1)
            .extractingResultOf("getMessageId").containsOnly(messageId);
    }

    private Predicate<MessageResult> messageInMailbox2() {
        return new Predicate<MessageResult>() {
            @Override
            public boolean apply(MessageResult input) {
                return input.getMailboxId().equals(mailbox2.getMailboxId());
            }
        };
    }

}