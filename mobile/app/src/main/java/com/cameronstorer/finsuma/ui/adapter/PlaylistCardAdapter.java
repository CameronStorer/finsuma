package com.cameronstorer.finsuma.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cameronstorer.finsuma.R;
import com.cameronstorer.finsuma.databinding.ItemLibraryAlbumBinding;
import com.cameronstorer.finsuma.glide.CustomGlideRequest;
import com.cameronstorer.finsuma.interfaces.ClickCallback;
import com.cameronstorer.finsuma.subsonic.models.Playlist;
import com.cameronstorer.finsuma.util.Constants;
import com.cameronstorer.finsuma.util.TileSizeManager;

import java.util.Collections;
import java.util.List;

/** Finsuma: playlists as cover cards (same tile as albums), for the home Playlists row. */
public class PlaylistCardAdapter extends RecyclerView.Adapter<PlaylistCardAdapter.ViewHolder> {
    private final ClickCallback click;
    private List<Playlist> playlists = Collections.emptyList();
    private int sizePx = 400;
    private boolean sizeFixed;

    /** Pinch to zoom sets the cover size; it then wins over the default tile size. */
    public void setSizePx(int px) {
        sizePx = px;
        sizeFixed = true;
    }

    public PlaylistCardAdapter(ClickCallback click) {
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
    public void onBindViewHolder(ViewHolder holder, int position) {
        ViewGroup.LayoutParams lp = holder.item.albumCoverImageView.getLayoutParams();
        lp.width = sizePx;
        lp.height = sizePx;
        holder.item.albumCoverImageView.setLayoutParams(lp);

        Playlist playlist = playlists.get(position);
        holder.item.albumNameLabel.setText(playlist.getName());
        holder.item.artistNameLabel.setText(holder.itemView.getContext().getResources()
                .getQuantityString(R.plurals.finsuma_song_count, playlist.getSongCount(), playlist.getSongCount()));

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), playlist.getCoverArtId(), CustomGlideRequest.ResourceType.Playlist)
                .build()
                .fallback(com.cameronstorer.finsuma.util.PlaylistCover.drawable(playlist.getId(), playlist.getName()))
                .error(com.cameronstorer.finsuma.util.PlaylistCover.drawable(playlist.getId(), playlist.getName()))
                .into(holder.item.albumCoverImageView);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public void setItems(List<Playlist> playlists) {
        this.playlists = playlists;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final ItemLibraryAlbumBinding item;

        ViewHolder(ItemLibraryAlbumBinding item) {
            super(item.getRoot());
            this.item = item;
            itemView.setOnClickListener(v -> click.onPlaylistClick(bundle()));
            itemView.setOnLongClickListener(v -> {
                click.onPlaylistLongClick(bundle());
                return true;
            });
        }

        private Bundle bundle() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.PLAYLIST_OBJECT, playlists.get(getBindingAdapterPosition()));
            return bundle;
        }
    }
}
