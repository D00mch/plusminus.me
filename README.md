# plus-minus

Math game.

check current web-version on [plusminus.me][1]
(currently banned in Russia by Roskomnadzor, use proxy or check demo published with [heroku][4])

<img src="https://github.com/Liverm0r/Plus-Minus-Fullstack/blob/dev/resources/public/img/gif/demo.gif" alt="alt text" width="286" height="514">

published with [digitalocean.com][3]

check Android version on [Google play][2]

[1]: https://plusminus.me
[2]: https://play.google.com/store/apps/details?id=com.livermor.plusminus
[3]: https://m.do.co/c/edb551a6bfca
[4]: https://plus-minus-game.herokuapp.com/

## Status: in progress 

### TODOS
- event driven database;
- cache on server for single-player game;
- PWA;
- chat

## Prerequisites

You will need [Leiningen][3] 2.0 or above installed.

[3]: https://github.com/technomancy/leiningen

## Running

To start it in dev you need to provide dev-config.edn file (put it in project root): 
```clojure
{
 ;; mandatory (remember to change your dbname, login and passowrd)
 ;; ----------------------
 :database-url "jdbc:postgresql://localhost/dbname?user=login&password=password"
 :dev  true

 ;; optional params below
 ;; ----------------------
 :port 3000
 ;; when :nrepl-port is set the application starts the nREPL server on load
 :nrepl-port 7000

 ;; for oauth
 :oauth-consumer-key    "your google oauth key"
 :oauth-consumer-secret "your google oauth secret"
 :domain                "http://localhost:3000"

 ;; key for your session cookie
 :session-store-key "some_16chars_key"
}
```
To start a web server for the application, run:

    lein run 

## License
```
Copyright 2019 Artur Dumchev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
