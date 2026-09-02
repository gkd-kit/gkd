package li.gkd.selector

import kotlinx.serialization.json.Json
import li.songe.json5.decodeFromJson5String

private fun String.toTestNode(): TestNode = Json.decodeFromJson5String(this)

internal fun testTree(): TestNode = """
    {
      key: 'root',
      name: 'Root',
      attributes: { id: 'root' },
      children: [
        {
          key: 'header',
          name: 'android.widget.LinearLayout',
          attributes: { vid: 'header' },
          children: [
            {
              key: 'title',
              name: 'android.widget.TextView',
              attributes: {
                text: 'Title',
                width: 100,
                clickable: false,
              },
            },
            {
              key: 'confirm',
              name: 'android.widget.Button',
              attributes: {
                text: 'Confirm',
                width: 120,
                clickable: true,
              },
            },
            {
              key: 'cancel',
              name: 'android.widget.Button',
              attributes: {
                text: 'Cancel',
                width: 80,
                clickable: true,
              },
            },
          ],
        },
        {
          key: 'content',
          name: 'android.widget.FrameLayout',
          attributes: { vid: 'content' },
          children: [
            {
              key: 'card1',
              name: 'Card',
              children: [
                {
                  key: 'alpha',
                  name: 'android.widget.TextView',
                  attributes: { text: 'Alpha42' },
                },
                {
                  key: 'logo',
                  name: 'android.widget.ImageView',
                  attributes: { desc: 'logo' },
                },
              ],
            },
            {
              key: 'card2',
              name: 'Card',
              children: [
                {
                  key: 'beta',
                  name: 'android.widget.TextView',
                  attributes: { text: 'Beta7' },
                },
                {
                  key: 'open',
                  name: 'android.widget.Button',
                  attributes: { text: 'Open', clickable: true },
                },
              ],
            },
          ],
        },
        {
          key: 'footer',
          name: 'Footer',
          attributes: { text: 'Done' },
        },
      ],
    }
""".toTestNode()

internal fun compileSelector(source: String): Selector = Selector.compile(source).value
