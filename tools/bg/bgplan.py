import json
HEAD = ("Vertical phone wallpaper background, no characters, no animals, no people, no text. "
        "Camera slightly elevated looking gently down, so the ground fills the entire lower 60 percent of the frame "
        "as one wide, flat, open, empty stage with nothing standing in the middle or foreground; all scenery sits along the far edge and the sides. ")
TAIL = " Style: cute stylized-realistic 3D animated film look, soft global illumination, gentle depth of field on the far scenery, rich detail, crisp sharp textures, high resolution."
TIMES = [
 ('morning', "soft early morning light, low warm sun from the left, a light haze, fresh pastel sky with a few clouds"),
 ('noon', "bright midday, high sun, vivid saturated colours, short soft shadows, clear blue sky"),
 ('sunset', "golden hour sunset, orange and pink sky, long warm shadows across the ground, glowing rim light"),
 ('night', "night, deep blue moonlit sky full of stars, soft cool moonlight on the ground, a few warm glowing lights where they fit, still clearly readable and not too dark"),
]
ENVS = [
 ('dog_snowcabin', "A snowy clearing in front of a cosy log cabin: smooth untouched snow covers the ground, the cabin with a glowing window and a woodpile sits at the far left, snow-covered pine trees along the far edge and the right side, a red sled at the far right."),
 ('cat_livingroom', "A cosy living room seen from inside: warm honey wooden floor with a big soft round rug covers the ground, at the far wall a tall window with sheer curtains, a plush sofa on the left, a cat tree and potted plants on the right, a small yarn ball at the bottom left corner."),
 ('cat_rooftop', "A rooftop garden terrace above a city: clean terracotta floor tiles cover the ground, a low brick parapet along the far edge with potted flowers and string lights, a little wooden bench on the right, the city skyline beyond the parapet."),
 ('parrot_rainforest', "A rainforest clearing: soft mossy ground with tiny flowers covers the ground, giant tropical trees with hanging vines along the far edge and both sides, big fern leaves at the sides, a distant waterfall in the background."),
 ('parrot_beach', "A tropical beach: smooth white sand covers the ground, the turquoise sea and gentle waves along the far edge, leaning palm trees on the left and right sides, a few shells at the bottom corners."),
 ('duck_pond', "A spring meadow by a pond: soft short green grass covers the ground, a small calm pond with reeds and lily pads along the far edge, little white daisies scattered at the sides, a weeping willow on the right."),
 ('duck_blossom', "A blossom garden: a wide soft lawn sprinkled with fallen pink petals covers the ground, cherry blossom trees along the far edge and both sides, a little wooden bridge far in the background."),
 ('squirrel_autumn', "An autumn forest floor: a carpet of orange, red and yellow fallen leaves covers the ground, big oak trees with golden leaves along the far edge and both sides, a few acorns and a mushroom at the bottom corners."),
 ('squirrel_park', "A city park: a wide neat lawn covers the ground, a curving gravel path and a wooden park bench and an old lamp post along the far edge, leafy trees at both sides, city buildings far behind the trees."),
 ('robot_workshop', "A tidy tech workshop: a smooth clean concrete floor with faint painted lines covers the ground, a long workbench with tools, screens and glowing gadgets along the far wall, shelves with spare parts on the right, a small charging pad on the floor at the bottom left corner."),
 ('robot_moonbase', "A moon base: smooth grey lunar ground with soft small craters covers the ground, white domes and a little antenna tower of a base along the far edge, the Earth hanging in the black starry sky."),
 ('reaper_graveyard', "A cute spooky graveyard: a misty flat lawn of dark grass covers the ground, crooked friendly tombstones, a small iron fence and a twisted bare tree along the far edge, carved jack-o'-lanterns at the sides."),
 ('reaper_pumpkin', "A pumpkin patch: a flat field of soft soil and straw covers the ground, rows of big orange pumpkins and a scarecrow along the far edge, a red barn on the left, autumn trees on the right."),
 ('fish_reef', "Underwater coral reef: a wide flat bed of smooth pale sand covers the ground, colourful coral and sea anemones along the far edge and both sides, swaying seaweed, light rays from the surface, floating bubbles."),
 ('fish_ship', "Underwater by a sunken ship: a wide flat sandy sea floor covers the ground, an old wooden shipwreck covered in coral far in the background, a treasure chest at the side, swaying seaweed, light rays from the surface."),
 ('mochi_candy', "A pastel candy land: a smooth soft pink-and-mint marshmallow ground covers the ground, lollipop trees, gumdrop hills and a cupcake house along the far edge, candy canes at the sides."),
 ('mochi_cloud', "A cloud nursery in the sky: a flat soft fluffy white cloud floor covers the ground, rounded cloud hills along the far edge, a pastel rainbow arching across the sky, little star mobiles hanging at the sides."),
 ('lion_savanna', "An African savanna: a wide flat plain of short golden grass covers the ground, acacia trees and a big rock outcrop along the far edge, distant mountains."),
 ('lion_waterhole', "A savanna watering hole: flat dry earth and short grass covers the ground, a calm waterhole with reflections along the far edge, a large baobab tree on the right, tall grass on the left."),
]
scenes = []
# extra times for the backyard pilot (already have morning as PulsifyBG-scene-01)
for i, (tn, td) in enumerate(TIMES[1:]):
    scenes.append(dict(type='image', aspect_ratio='9:16', download_quality='upscaled',
        reference_images=['asset:PulsifyBG-scene-01'] if i == 0 else None, reference_previous=(i > 0),
        prompt=f"Exact same backyard scene, same camera, same composition and the same objects in the same places as the reference image; only the time of day changes: {td}. No characters, no text.{TAIL}"))
names = ['dog_backyard_' + t for t, _ in TIMES[1:]]
for key, desc in ENVS:
    for i, (tn, td) in enumerate(TIMES):
        if i == 0: p = HEAD + desc + f" Light: {td}." + TAIL
        else: p = f"Exact same scene, same camera, same composition and the same objects in the same places as the reference image; only the time of day changes: {td}. No characters, no text.{TAIL}"
        sc = dict(type='image', aspect_ratio='9:16', download_quality='upscaled', prompt=p)
        if i > 0: sc['reference_previous'] = True
        scenes.append(sc); names.append(f'{key}_{tn}')
for s in scenes:
    for k in list(s):
        if s[k] is None: del s[k]
json.dump(scenes, open('/home/claude/batch2/bg_scenes.json', 'w'))
json.dump(names, open('/home/claude/batch2/bg_names.json', 'w'))
print(len(scenes))
