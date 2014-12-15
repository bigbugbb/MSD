from django.conf.urls import url

from naivebayes import views

urlpatterns = [

    url(r'^range/$', views.range_index, name='range_index'),    

    url(r'^range/(?P<range_id>[0-9]+)/$', views.range_detail, name='range_detail'),

    url(r'^nbmodel/$', views.nbmodel_index, name='nbmodel_index'),    

    url(r'^nbmodel/(?P<year>[0-9]+)/$', views.nbmodel_detail, name='nbmodel_detail'),

    url(r'^count/$', views.count_index, name='count_index'),    

    url(r'^count/(?P<year>[0-9]+)/$', views.count_detail, name='count_detail'),

    url(r'^updates/$', views.update_naivebayes, name='update_naivebayes'),
]