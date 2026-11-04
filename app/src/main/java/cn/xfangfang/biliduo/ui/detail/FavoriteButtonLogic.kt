package cn.xfangfang.biliduo.ui.detail

enum class FavAction {
    OpenPicker,
    CancelFavorite
}

fun favoriteActionForClick(isFavorited: Boolean): FavAction =
    if (isFavorited) FavAction.CancelFavorite else FavAction.OpenPicker

fun favoriteButtonEnabled(isToggling: Boolean, loading: Boolean): Boolean =
    !isToggling && !loading
