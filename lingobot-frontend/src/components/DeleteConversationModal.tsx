import React from 'react';

interface DeleteConversationModalProps {
  isOpen: boolean;
  conversationTitle: string;
  onClose: () => void;
  onConfirm: () => void;
}

const DeleteConversationModal: React.FC<DeleteConversationModalProps> = ({
  isOpen,
  conversationTitle,
  onClose,
  onConfirm,
}) => {
  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="auth-modal-overlay" onClick={handleOverlayClick}>
      <div className="auth-modal delete-conversation-modal">
        <div className="delete-modal-header">
          <h2>删除聊天？</h2>
        </div>

        <div className="delete-modal-content">
          <p className="delete-modal-message">
            确定要删除 <strong>"{conversationTitle}"</strong> 吗？
          </p>
        </div>

        <div className="delete-modal-actions">
          <button 
            type="button" 
            className="delete-modal-cancel-btn" 
            onClick={onClose}
          >
            取消
          </button>
          <button 
            type="button" 
            className="delete-modal-delete-btn" 
            onClick={onConfirm}
          >
            删除
          </button>
        </div>
      </div>
    </div>
  );
};

export default DeleteConversationModal;
