---
layout: post
title: Kamon meets the World!
date: 2014-03-17
categories: teamblog
tags: announcement
---

Dear community,

Some time ago we started working with Akka and Spray, and it has been awesome! The more we learned, the more we wanted
to use these toolkits, and it didn't take long until our coworkers started experimenting as well, I guess that's what
happens when you find something that brings more joy than usual to everyday coding.

As the prototypes turned into applications a concern started to rise: how are we going to monitor this applications on
production? Throwing our default monitoring tool at the apps gave us an empty dashboard with no metrics at all and
acquiring another monitoring tool wasn't an option. What did we do?, we started building something that could fill all
the blanks.

Let me tell you that learning how to use Akka/Spray and at the same time developing a tool for monitoring apps made on
top of that is not the way to go! We found many issues on the way and realize that we were not alone, many people was
facing the same problems we had, and after all the community has given to all of us during many years, why don't we make
something that can benefit not just us but all developers using Akka and Spray? Then Kamon was born.

Kamon is already being used by many teams close to us, and some people from around the globe gave Kamon a try and seem
to be happy with it, we hope now that you can try Kamon and let us know what you think and how we can make Kamon better.
We are currently short on documentation, but feel free to ask anything you need through the mailing list! more docs are
on the oven.

So, what are you waiting for? go and learn about [tracing in Kamon] and [get started] right now!

[tracing in Kamon]: /core/tracing/basics/
[get started]: /introduction/get-started/