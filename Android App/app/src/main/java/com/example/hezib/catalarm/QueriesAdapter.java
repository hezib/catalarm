package com.example.hezib.catalarm;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * Created by hezib on 18/11/2017.
 */

public class QueriesAdapter extends RecyclerView.Adapter<QueriesAdapter.QuesriesAdapterViewHolder> {

    private List<DatabaseDocument> mDocsList;

    private final QueriesAdapterOnClickHandler mClickHandler;

    public QueriesAdapter(QueriesAdapterOnClickHandler clickHandler) {
        mClickHandler = clickHandler;
    }

    public void setDocumentsList(List<DatabaseDocument> list) {
        mDocsList = list;
        notifyDataSetChanged();
    }

    @Override
    public QuesriesAdapterViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        int layoutIdForListItem = R.layout.queries_list_item;
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(layoutIdForListItem, parent, false);
        return new QuesriesAdapterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(QuesriesAdapterViewHolder holder, int position) {
        DatabaseDocument document = mDocsList.get(position);
        holder.itemView.setTag(document);
        holder.mDateTextView.setText(document.day + "/" + document.month + "/" + document.year);
        holder.mTimeTextView.setText(document.hour + ":" + document.min + ":" + document.sec);

    }

    @Override
    public int getItemCount() {
        if(mDocsList == null) return 0;
        return mDocsList.size();
    }

    public interface QueriesAdapterOnClickHandler {
        void onClick(DatabaseDocument document);
    }


    public class QuesriesAdapterViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{

        public final TextView mDateTextView;
        public final TextView mTimeTextView;

        public QuesriesAdapterViewHolder(View itemView) {
            super(itemView);
            mDateTextView = (TextView)itemView.findViewById(R.id.tv_date);
            mTimeTextView = (TextView)itemView.findViewById(R.id.tv_time);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int adapterPosition = getAdapterPosition();
            DatabaseDocument doc = mDocsList.get(adapterPosition);
            mClickHandler.onClick(doc);
        }
    }
}
