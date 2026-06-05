package com.atakmap.android.skeleton;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DeflockAdapter extends RecyclerView.Adapter<DeflockAdapter.ViewHolder> {

    public interface OnAlprClickListener {
        void onAlprClick(Alpr alpr);
    }

    private final List<Alpr> items = new ArrayList<>();
    private OnAlprClickListener listener;

    public void setOnAlprClickListener(OnAlprClickListener l) {
        this.listener = l;
    }

    public void setAlprs(List<Alpr> alprs) {
        items.clear();
        items.addAll(alprs);
        notifyDataSetChanged();
    }

    public List<Alpr> getAlprs() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.deflock_list_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Alpr alpr = items.get(position);
        holder.tvManufacturer.setText(alpr.getManufacturer());
        String op = alpr.getOperator();
        if (op.isEmpty()) {
            holder.tvOperator.setVisibility(View.GONE);
        } else {
            holder.tvOperator.setVisibility(View.VISIBLE);
            holder.tvOperator.setText(op);
        }
        String dir = alpr.getDirection();
        if (dir.isEmpty()) {
            holder.tvDirection.setVisibility(View.GONE);
        } else {
            holder.tvDirection.setVisibility(View.VISIBLE);
            holder.tvDirection.setText(dir + "°");
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onAlprClick(alpr);
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvManufacturer;
        final TextView tvOperator;
        final TextView tvDirection;

        ViewHolder(View v) {
            super(v);
            tvManufacturer = v.findViewById(R.id.tv_manufacturer);
            tvOperator = v.findViewById(R.id.tv_operator);
            tvDirection = v.findViewById(R.id.tv_direction);
        }
    }
}
