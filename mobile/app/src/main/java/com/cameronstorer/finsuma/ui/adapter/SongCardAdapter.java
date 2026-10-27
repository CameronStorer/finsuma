package com.cameronstorer.finsuma.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cameronstorer.finsuma.databinding.ItemLibraryAlbumBinding;
import com.cameronstorer.finsuma.glide.CustomGlideRequest;
import com.cameronstorer.finsuma.interfaces.ClickCallback;
import com.cameronstorer.finsuma.subsonic.models.Child;
import com.cameronstorer.finsuma.util.Constants;
import com.cameronstorer.finsuma.util.MusicUtil;
import com.cameronstorer.finsuma.util.TileSizeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finsuma: songs as cover tiles for grid view. Mirrors the list adapter's (sorted/filtered) items. */
public class SongCardAdapter extends RecyclerView.Adapter<SongCardAdapter.ViewHolder> {
    private final ClickCallback click;
    private List<Child> songs = Collections.emptyList();
    private int sizePx = 400;
    private boolean sizeFixed;

    /** Pinch to zoom sets the cover size; it then wins over the default tile size. */
    public void setSizePx(int px) {
        sizePx = px;
        sizeFixed = true;
    }

    public SongCardAdapter(ClickCallback click) {
        this.click = click;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryAlbumBinding view = ItemLibraryAlbumBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        TileSizeManager.getInstance().calculateTileSize(parent.getContext());
        if (!sizeFixed) sizePx = TileSizeManager.getInstance().getTileSizePx(parent.getContext());
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ViewGroup.LayoutParams lp = holder.item.albumCoverImageView.getLayoutParams();
        lp.width = sizePx;
        lp.height = sizePx;
        holder.item.albumCoverImageView.setLayoutParams(lp);

        Child song = songs.get(position);
        holder.item.albumNameLabel.setText(song.getTitle());
        holder.item.artistNameLabel.setText(song.getArtist());
        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.albumCoverImageView);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public void setItems(List<Child> songs) {
        this.songs = new ArrayList<>(songs);
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final ItemLibraryAlbumBinding item;

        ViewHolder(ItemLibraryAlbumBinding item) {
            super(item.getRoot());
            this.item = item;
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(MusicUtil.limitPlayableMedia(songs, pos)));
                bundle.putInt(Constants.ITEM_POSITION, MusicUtil.getPlayableMediaPosition(songs, pos));
                click.onMediaClick(bundle);
            });
            itemView.setOnLongClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.TRACK_OBJECT, songs.get(getBindingAdapterPosition()));
                bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());
                click.onMediaLongClick(bundle);
                return true;
            });
        }
    }
}
