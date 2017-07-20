package com.quickblox.q_municate.ui.adapters.chats;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.q_municate_chat_service.entity.QBMessage;
import com.quickblox.chat.model.QBChatDialog;
import com.quickblox.chat.model.QBChatMessage;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.ui.activities.base.BaseActivity;
import com.quickblox.q_municate.utils.DateUtils;
import com.quickblox.q_municate.utils.FileUtils;
import com.quickblox.q_municate_core.models.AppSession;
import com.quickblox.q_municate_core.models.CombinationMessage;
import com.quickblox.q_municate_core.qb.commands.chat.QBUpdateStatusMessageCommand;
import com.quickblox.q_municate_core.utils.DbUtils;
import com.quickblox.q_municate_db.managers.DataManager;
import com.quickblox.q_municate_db.models.State;
import com.quickblox.ui.kit.chatmessage.adapter.QBMessagesAdapter;
import com.quickblox.users.model.QBUser;
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersAdapter;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;


public class BaseChatMessagesAdapter extends QBMessagesAdapter<QBMessage> implements StickyRecyclerHeadersAdapter<RecyclerView.ViewHolder> {

    private static final String TAG = BaseChatMessagesAdapter.class.getSimpleName();
    protected static final int TYPE_REQUEST_MESSAGE = 5;
    protected QBUser currentUser;
    protected final BaseActivity baseActivity;
    protected FileUtils fileUtils;

    private DataManager dataManager;
    protected QBChatDialog chatDialog;

    BaseChatMessagesAdapter(BaseActivity baseActivity, QBChatDialog dialog, List<QBMessage> chatMessages) {
        super(baseActivity.getBaseContext(), chatMessages);
        this.baseActivity = baseActivity;
        chatDialog = dialog;
        currentUser = AppSession.getSession().getUser();
        fileUtils = new FileUtils();
        dataManager = DataManager.getInstance();
    }

    @Override
    public long getHeaderId(int position) {
        QBMessage combinationMessage = getItem(position);
        return DateUtils.toShortDateLong(combinationMessage.getDateSent());
    }

    @Override
    public RecyclerView.ViewHolder onCreateHeaderViewHolder(ViewGroup parent) {
        View view = inflater.inflate(R.layout.item_chat_sticky_header_date, parent, false);
        return new RecyclerView.ViewHolder(view) {
        };
    }

    @Override
    public void onBindHeaderViewHolder(RecyclerView.ViewHolder holder, int position) {
        View view = holder.itemView;

        TextView headerTextView = (TextView) view.findViewById(R.id.header_date_textview);
        QBMessage combinationMessage = getItem(position);
        headerTextView.setText(DateUtils.toTodayYesterdayFullMonthDate(combinationMessage.getDateSent()));
    }


    @Override
    public int getItemViewType(int position) {
        QBMessage combinationMessage = getItem(position);
        if (combinationMessage.getNotificationType() != null) {
            return TYPE_REQUEST_MESSAGE;
        }
        return super.getItemViewType(position);
    }

    @Override
    protected int customViewType(int position) {
        //TODO VT temp fix before implementation attachment's type 'audio' and 'video' in ChatMessagesAdapter-android lib
        return isIncoming(getItem(position)) ? TYPE_TEXT_LEFT : TYPE_TEXT_RIGHT;
    }

    @Override
    protected RequestListener getRequestListener(QBMessageViewHolder holder, int position) {
        QBMessage chatMessage = getItem(position);

        return new ImageRequestListener((ImageAttachHolder) holder, isIncoming(chatMessage));
    }

    @Override
    public String obtainAvatarUrl(int valueType, QBMessage chatMessage) {
        return null;
    }

    private void resetAttachUI(ImageAttachHolder viewHolder) {
        setViewVisibility(viewHolder.itemView.findViewById(R.id.msg_bubble_background_attach), View.GONE);
        setViewVisibility(viewHolder.itemView.findViewById(R.id.msg_image_avatar), View.GONE);
    }

    protected void showAttachUI(ImageAttachHolder viewHolder, boolean isIncoming) {
        if (isIncoming) {
            setViewVisibility(viewHolder.itemView.findViewById(R.id.msg_image_avatar), View.VISIBLE);
        }
        setViewVisibility(viewHolder.itemView.findViewById(R.id.msg_bubble_background_attach), View.VISIBLE);
    }

    protected void setViewVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    public boolean isEmpty() {
        return chatMessages.size() == 0;
    }

    @Override
    protected boolean isIncoming(QBMessage chatMessage) {
        return !(currentUser.getId().equals(chatMessage.getSenderId()));
    }

    protected void updateMessageState(QBMessage message, QBChatDialog dialog) {
        /*if (!State.READ.equals(message.getState()) && baseActivity.isNetworkAvailable()) {
            message.setState(State.READ);
            Log.d(TAG, "updateMessageState");

            message.setState(State.READ);
            QBUpdateStatusMessageCommand.start(baseActivity, dialog, message, true);
        }*/
    }

    @Override
    protected void onBindViewAttachLeftHolder(ImageAttachHolder holder, QBMessage chatMessage, int position) {
        updateMessageState(chatMessage, chatDialog);
        super.onBindViewAttachLeftHolder(holder, chatMessage, position);
    }

    public void addAllInBegin(List<QBMessage> collection) {
        chatMessages.addAll(0, collection);
        notifyItemRangeInserted(0, collection.size());
    }

    public void addAllInEnd(List<QBMessage> collection) {
        chatMessages.addAll(collection);
        notifyItemRangeInserted(chatMessages.size() - collection.size(), chatMessages.size());
    }

    public void setList(List <QBMessage> collection, boolean notifyDataChanged){
        chatMessages = collection;
        if (notifyDataChanged) {
            this.notifyDataSetChanged();
        }
    }

    public class ImageRequestListener implements RequestListener<String, GlideBitmapDrawable> {
        private ImageAttachHolder viewHolder;
        private Bitmap loadedImageBitmap;
        private boolean isIncoming;

        public ImageRequestListener(ImageAttachHolder viewHolder, boolean isIncoming) {
            this.viewHolder = viewHolder;
            this.isIncoming = isIncoming;
        }

        @Override
        public boolean onException(Exception e, String model, Target target, boolean isFirstResource) {
            updateUIAfterLoading();
            resetAttachUI(viewHolder);
            Log.d(TAG, "onLoadingFailed");
            return false;
        }

        @Override
        public boolean onResourceReady(GlideBitmapDrawable loadedBitmap, String imageUri, Target target, boolean isFromMemoryCache, boolean isFirstResource) {
            initMaskedImageView(loadedBitmap.getBitmap());
            fileUtils.checkExistsFile(imageUri, loadedBitmap.getBitmap());
            return false;
        }

        protected void initMaskedImageView(Bitmap loadedBitmap) {
            loadedImageBitmap = loadedBitmap;
            viewHolder.attachImageView.setImageBitmap(loadedImageBitmap);

            showAttachUI(viewHolder, isIncoming);

            updateUIAfterLoading();
        }

        private void updateUIAfterLoading() {
            if (viewHolder.attachmentProgressBar != null) {
                setViewVisibility(viewHolder.attachmentProgressBar, View.GONE);
            }
        }
    }

    protected static class RequestsViewHolder extends QBMessageViewHolder {
        @Nullable
        @BindView(R.id.message_textview)
        TextView messageTextView;

        @Nullable
        @BindView(R.id.time_text_message_textview)
        TextView timeTextMessageTextView;

        @Nullable
        @BindView(R.id.accept_friend_imagebutton)
        ImageView acceptFriendImageView;

        @Nullable
        @BindView(R.id.divider_view)
        View dividerView;

        @Nullable
        @BindView(R.id.reject_friend_imagebutton)
        ImageView rejectFriendImageView;


        public RequestsViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, itemView);
        }
    }
}
