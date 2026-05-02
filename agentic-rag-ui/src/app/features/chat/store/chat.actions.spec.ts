import * as ChatActions from './chat.actions';
import type { Message } from './chat.state';
import type { StreamEvent } from '../../../core/services/streaming-api.service';

describe('ChatActions', () => {

  describe('Conversation Actions', () => {
    it('createConversation doit avoir le type [Chat] Create Conversation sans payload', () => {
      const action = ChatActions.createConversation();
      expect(action.type).toBe('[Chat] Create Conversation');
    });

    it('loadConversations doit avoir le type [Chat] Load Conversations sans payload', () => {
      const action = ChatActions.loadConversations();
      expect(action.type).toBe('[Chat] Load Conversations');
    });

    it('loadConversationsSuccess doit inclure le tableau conversations dans le payload', () => {
      const action = ChatActions.loadConversationsSuccess({ conversations: [] });
      expect(action.type).toBe('[Chat] Load Conversations Success');
      expect(action.conversations).toEqual([]);
    });

    it('setActiveConversation doit inclure conversationId dans le payload', () => {
      const action = ChatActions.setActiveConversation({ conversationId: 'c1' });
      expect(action.type).toBe('[Chat] Set Active Conversation');
      expect(action.conversationId).toBe('c1');
    });

    it('deleteConversation doit inclure conversationId dans le payload', () => {
      const action = ChatActions.deleteConversation({ conversationId: 'c2' });
      expect(action.type).toBe('[Chat] Delete Conversation');
      expect(action.conversationId).toBe('c2');
    });
  });

  describe('Message Actions', () => {
    it('sendMessage doit avoir le type [Chat] Send Message et le payload content', () => {
      const action = ChatActions.sendMessage({ content: 'Bonjour' });
      expect(action.type).toBe('[Chat] Send Message');
      expect(action.content).toBe('Bonjour');
    });

    it('sendMessage doit accepter un conversationId optionnel', () => {
      const action = ChatActions.sendMessage({ content: 'Hello', conversationId: 'c1' });
      expect(action.conversationId).toBe('c1');
    });

    it('addUserMessage doit inclure le message complet dans le payload', () => {
      const message: Message = {
        id: 'msg-1',
        role: 'user',
        content: 'Test',
        timestamp: new Date(),
        status: 'complete'
      };
      const action = ChatActions.addUserMessage({ message });
      expect(action.type).toBe('[Chat] Add User Message');
      expect(action.message).toEqual(message);
    });

    it('addAssistantMessage doit inclure messageId dans le payload', () => {
      const action = ChatActions.addAssistantMessage({ messageId: 'asst-1' });
      expect(action.type).toBe('[Chat] Add Assistant Message');
      expect(action.messageId).toBe('asst-1');
    });
  });

  describe('Streaming Actions', () => {
    it('streamConnected doit encapsuler le StreamEvent dans event', () => {
      const event: StreamEvent = { type: 'connected', sessionId: 's1' };
      const action = ChatActions.streamConnected({ event });
      expect(action.type).toBe('[Chat] Stream Connected');
      expect(action.event.sessionId).toBe('s1');
    });

    it('streamToken doit encapsuler le StreamEvent dans event', () => {
      const event: StreamEvent = { type: 'token', text: 'hello' };
      const action = ChatActions.streamToken({ event });
      expect(action.type).toBe('[Chat] Stream Token');
      expect(action.event.text).toBe('hello');
    });

    it('streamComplete doit encapsuler le StreamEvent dans event', () => {
      const event: StreamEvent = { type: 'complete' };
      const action = ChatActions.streamComplete({ event });
      expect(action.type).toBe('[Chat] Stream Complete');
      expect(action.event.type).toBe('complete');
    });

    it('streamError doit avoir error dans le payload', () => {
      const action = ChatActions.streamError({ error: 'Network failure' });
      expect(action.type).toBe('[Chat] Stream Error');
      expect(action.error).toBe('Network failure');
    });

    it('cancelStream doit avoir le type [Chat] Cancel Stream sans payload', () => {
      const action = ChatActions.cancelStream();
      expect(action.type).toBe('[Chat] Cancel Stream');
    });

    it('cancelStreamSuccess doit avoir le type [Chat] Cancel Stream Success sans payload', () => {
      const action = ChatActions.cancelStreamSuccess();
      expect(action.type).toBe('[Chat] Cancel Stream Success');
    });
  });

  describe('Input & Error Actions', () => {
    it('updateInputText doit inclure text dans le payload', () => {
      const action = ChatActions.updateInputText({ text: 'ma requête' });
      expect(action.type).toBe('[Chat] Update Input Text');
      expect(action.text).toBe('ma requête');
    });

    it('clearInputText doit avoir le type [Chat] Clear Input Text sans payload', () => {
      const action = ChatActions.clearInputText();
      expect(action.type).toBe('[Chat] Clear Input Text');
    });

    it('clearError doit avoir le type [Chat] Clear Error sans payload', () => {
      const action = ChatActions.clearError();
      expect(action.type).toBe('[Chat] Clear Error');
    });
  });
});
