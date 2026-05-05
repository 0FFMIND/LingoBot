import React from 'react';

export interface UserImageMessageProps {
  imageData: string;
  imageFormat?: string;
}

const UserImageMessage: React.FC<UserImageMessageProps> = ({ imageData, imageFormat }) => {
  const imageUrl = `data:image/${imageFormat || 'png'};base64,${imageData}`;

  return (
    <div className="user-image-message-english">
      <img
        src={imageUrl}
        alt="用户上传的图片"
        className="message-image-english"
        style={{ maxWidth: '300px', maxHeight: '300px', borderRadius: '12px', cursor: 'pointer' }}
        onClick={() => window.open(imageUrl, '_blank')}
      />
    </div>
  );
};

export default UserImageMessage;
