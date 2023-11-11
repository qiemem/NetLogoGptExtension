# NetLogo GPT Extension

An NetLogo extension for letting your agents communicate and make decisions using GPT-3.5 or GPT-4.

## Setup

First, you must give the extension your API key.
I recommend putting your key in a file in the same directory as the model and then reading it like so:

```
extensions [ gpt ]

to setup
  clear-all

  file-open "apikey"
  gpt:set-api-key file-read-line
  file-close

  reset-ticks
end
```

If you don't have an OpenAI API, you can create a new one here: https://platform.openai.com/account/api-keys

To get the demo models working, you *must* put a file named `apikey` in the same directory as the model which has a single line containing your API key.

## Primitives

### `gpt:set-api-key <string>`

Tells the extension to use `<string>` as your OpenAI API key.

### `gpt:set-model <string>`

Tells the extension which OpenAI GPT model to use.
Defaults to `gpt-3.5-turbo`; set to `gpt-4` to use GPT-4.
Other models are listed under `/v1/chat/completions` here: https://platform.openai.com/docs/models/overview

### `gpt:chat <string>`

Sends `<string>` to the model and waits for a response.
Each agent tracks maintains an independent conversational history (including the observer).
Thus, `turtle 0` will "remember" what you've talked about in the past.
Response times are typically a couple seconds.

Example:

```
observer> show gpt:chat "Pick a color. Format your response as a JSON object with the keys `color` and `reason`."
observer: "{\n  \"color\": \"blue\",\n  \"reason\": \"I find blue to be a calming and soothing color. It reminds me of the sky and the ocean, which brings a sense of peacefulness and tranquility.\"\n}"
```

### `gpt:chat-async <string>`

Like `gpt:chat`, but does not wait for response.
Instead, it returns a reporter.
Using `runresult` on the reporter will then wait for the response.
This allows you to get many responses from the the AI model in parallel.
This is useful when you have multiple agents that need to get a response from the AI model at the same time.

Example usage:

Given:
```
turtles-own [ response-reporter ]
```

```
observer> ask turtles [ set response-reporter gpt:chat-async "Pretend you are an agent in a network. What would you like say to the other agents you are connected with? Respond with one short sentence." ]
observer> ask turtles [ show runresult response-reporter ]
(turtle 0): "Let's work together to achieve our common goals."
(turtle 5): "Let's work together to achieve our common goal."
(turtle 1): "Let's work together to maximize our efficiency and achieve our common goals."
(turtle 3): "Looking forward to collaborating with all of you!"
(turtle 4): "Let's collaborate and achieve our goals together."
(turtle 2): "Let's collaborate and work together to achieve our goals efficiently."
```

### `gpt:history`

Reports a list of the entire conversation for a given agent.
Each item in the list is a pair, with the first element being who sent the message role (either `system`, `assistant`, or `user`) and the second element being the message itself.

Example:

```
observer> foreach gpt:history print
[user Pick a color. Format your response as a JSON object with the keys `color` and `reason`.]
[assistant {
  "color": "blue",
  "reason": "I find blue to be a calming and soothing color. It reminds me of the sky and the ocean, which brings a sense of peacefulness and tranquility."
}]
```

### `gpt:set-history <list of string pairs>`

Sets the conversational history to given list.
Useful for sending a system message to inform agents of their role, managing agent knowledge, and pruning conversational context if it gets too big.

Example:

```
observer> gpt:set-history [ [ "system" "Pretend your name is Seymour and your favorite color is red." ] ]
observer> show gpt:chat "What is your name and favorite color? Format your response as JSON with the key `name` and `color`."
observer: "{\n  \"name\": \"Seymour\",\n  \"color\": \"red\"\n}"
```

## Tips

- Having many agents calling simultaneously can get pricy very quickly...
- Telling it to output as JSON and then reading it in using `table:from-json` is very useful for both getting multiple useful things from the same time as well as keeping it on task. Sometimes you need to tell it to not include formatting.
- If it keeps giving you "As an AI language model...", telling it to "pretend" can be useful. Also, again, formatting the output as JSON seems particularly effective in getting it to play the part you want.
- If its ignoring an aspect of the task or forgetting things it knows, asking it to include summaries of things in the JSON output can be helpful for directing its attention. For example, "What message would you like to send to the other agent? Your response must be a JSON object with the keys 'message', 'reason', and 'knowledge', where 'knowledge' is a JSON object where the keys are the names of the other agents and the values are a summary of what you've learned about them."