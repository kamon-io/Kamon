---
title: kamon | Team Blog | Documentation
layout: default
---

<div class="container">
  {% for post in site.posts %}
    <div class="row">
      <a href="{{ post.url }}"><h1>{{ post.title }}</h1></a>
      <small>{{ post.date | date_to_long_string }}</small>
      <hr>
      {{ post.content }}
      <hr>
    </div>
  {% endfor %}
</div>
